package com.chinatelecom.aigc.evaluate.job.core.util;

import javax.script.*;
import java.io.BufferedWriter;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
//import org.graalvm.polyglot.Context;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 通用脚本执行器：支持 JavaScript、Groovy、Python
 */
@Slf4j
@Component
public class ScriptExecutorUtils {

    @Value("${script.useSystemPython:false}")
    private boolean useSystemPython;

    private static final int MAX_CONTEXT_REUSE = 10;

    private static final ScriptEngineManager manager = new ScriptEngineManager();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /*

    private static final ThreadLocal<ContextWithMeta> threadLocalContext = new ThreadLocal<>();

    private static class ContextWithMeta {
        Context context;
        int useCount;

        ContextWithMeta(Context context) {
            this.context = context;
            this.useCount = 0;
        }
    }

    private org.graalvm.polyglot.Value convertMapToPythonDict(Context context, Map<String, Object> map) {
        StringBuilder pythonDictLiteral = new StringBuilder("{");
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            pythonDictLiteral.append("'")
                    .append(entry.getKey().replace("'", "\\'"))
                    .append("': ");
            if (entry.getValue() instanceof String) {
                pythonDictLiteral.append("'")
                        .append(entry.getValue().toString().replace("'", "\\'"))
                        .append("', ");
            } else {
                pythonDictLiteral.append(entry.getValue()).append(", ");
            }
        }
        if (pythonDictLiteral.length() > 1) {
            pythonDictLiteral.setLength(pythonDictLiteral.length() - 2); // 去掉最后一个逗号
        }
        pythonDictLiteral.append("}");

        // 一次性eval成Python dict
        return context.eval("python", pythonDictLiteral.toString());
    }

    */

    /**
     * 执行动态脚本
     * @param lang      脚本语言 例如 "javascript"、"groovy"、"python"
     * @param script    脚本源码
     * @param params    传给脚本的参数（可选）
     * @throws Exception 任何执行异常
     */
    public Object runScript(String lang, String script, Map<String, Object> params) throws Exception {
        if (lang == null || lang.isEmpty()) {
            throw new IllegalArgumentException("脚本语言不能为空");
        }
        if (script == null || script.isEmpty()) {
            throw new IllegalArgumentException("脚本内容不能为空");
        }

        if ("python".equalsIgnoreCase(lang)) {
            if (useSystemPython) {
                log.debug("runSystemPythonScript: {}", params);
                return runSystemPythonScript(buildPythonScript(script), params);
            } else {
                log.debug("runPythonScript: {}", params);
                return runPythonScript(buildPythonScript(script), params);
            }
        } else {
            ScriptEngine engine = manager.getEngineByName(lang);
            if (engine == null) {
                throw new RuntimeException("不支持的脚本语言: " + lang);
            }

            Bindings bindings = engine.createBindings();
            if (params != null && !params.isEmpty()) {
                bindings.putAll(params);
            }

            // 执行脚本并返回结果
            return engine.eval(script, bindings);  // 返回脚本执行结果
        }
    }

    public Object runPythonScript(String script, Map<String, Object> params) {
        return "";
    }
    /*
    public Object runPythonScript(String script, Map<String, Object> params) {
        ContextWithMeta ctxMeta = threadLocalContext.get();

        try {
            if (ctxMeta == null || ctxMeta.useCount >= MAX_CONTEXT_REUSE) {
                if (ctxMeta != null) {
                    ctxMeta.context.close(); // 清理旧 Context
                }
                ctxMeta = new ContextWithMeta(Context.newBuilder("python").allowAllAccess(true).build());
                threadLocalContext.set(ctxMeta);
            }

            ctxMeta.useCount++;

            Context context = ctxMeta.context;

            // 清理残留变量
            context.getBindings("python").removeMember("params");
            context.getBindings("python").removeMember("answer");
            context.getBindings("python").removeMember("main");

            // 执行脚本
            context.eval("python", script);

            org.graalvm.polyglot.Value mainFunc = context.getBindings("python").getMember("main");

            if (mainFunc != null && mainFunc.canExecute()) {
                org.graalvm.polyglot.Value pythonParam = convertMapToPythonDict(context, params);
                org.graalvm.polyglot.Value result = mainFunc.execute(pythonParam);
                return result.isNull() ? null : result.as(Object.class);
            }

            org.graalvm.polyglot.Value resultValue = context.getBindings("python").getMember("answer");
            return resultValue == null ? null : resultValue.as(Object.class);

        } catch (Exception e) {
            throw new RuntimeException("执行Python脚本失败: " + e.getMessage(), e);
        }
    }
    */


    /**
     * 使用系统 Python 执行脚本
     * @param script Python 脚本内容（字符串）
     * @param params 参数，会作为 JSON 传入 sys.argv[1]
     * @return 脚本标准输出的结构化结果
     * @throws Exception 执行失败抛出异常
     */
    public static Object runSystemPythonScript(String script, Map<String, Object> params) throws Exception {
        String paramJson = params == null ? "{}" : objectMapper.writeValueAsString(params);

        // 写入临时 Python 脚本文件
        File tempScript = File.createTempFile("script_", ".py");
        tempScript.deleteOnExit();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempScript), StandardCharsets.UTF_8))) {
            writer.write(script);
        }

        ProcessBuilder pb = new ProcessBuilder("python3", tempScript.getAbsolutePath(), paramJson);
        Process process = pb.start();

        // 异步读取 stdout 和 stderr
        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<String> stdoutFuture = executor.submit(() -> readStream(process.getInputStream()));
        Future<String> stderrFuture = executor.submit(() -> readStream(process.getErrorStream()));

        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            executor.shutdownNow();
            //throw new RuntimeException("Python 脚本执行超时");
            return  "";
        }

        String stdout = stdoutFuture.get();
        String stderr = stderrFuture.get();
        int exitCode = process.exitValue();
        executor.shutdown();

        if (exitCode != 0) {
            if (exitCode != 0) {
                return "ERROR: [Python 执行失败] \n退出码: " + exitCode +
                        "\n错误输出: " + stderr.trim() +
                        "\n标准输出: " + stdout.trim();
            }
            //throw new RuntimeException("Python 执行失败，退出码: " + exitCode + "\n错误输出: " + stderr + "\n标准输出: " + stdout);
        }

        String trimmed = stdout.trim();

        // 特殊处理 None / 空字符串
        if (trimmed.isEmpty() || "None".equals(trimmed)) {
            //return null;
            return  "";
        }

        // 尝试解析为 JSON
        try {
            return objectMapper.readValue(trimmed, Object.class);
        } catch (Exception e) {
            // 非 JSON 格式，返回纯文本
            return trimmed;
        }
    }

    private static String readStream(InputStream inputStream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }



    /**
     * 构建完整的 Python 脚本，动态拼接前端传来的 get_model_answer 实现
     * @param scriptSource 前端传来的 get_model_answer 函数实现
     * @return 拼接后的 Python 脚本
     */
    private String buildPythonScript(String scriptSource) {
        StringBuilder fullScript = new StringBuilder();

        fullScript.append(
                "import time\n" +
                        "import logging\n" +
                        "import json\n" +
                        "import hashlib\n" +
                        "import hmac\n" +
                        "import urllib.parse\n" +
                        "import sys\n" +
                        "import urllib.request\n\n" +

                        "logger = logging.getLogger(__name__)\n" +
                        "logging.basicConfig(level=logging.INFO)\n\n" +

                        scriptSource + "\n\n" +

                        "def main(params):\n" +
                        "    taskId = params.get('taskId')\n" +
                        "    question = params.get('question')\n" +
                        "    logger.debug(f'[main] taskId: {taskId}, question: {question}')\n\n" +

                        "    try:\n" +
                        "        answer = get_model_answer(question)\n" +
                        "        return answer if answer else 'ERROR:EmptyAnswer'\n" +
                        "    except BaseException as e:\n" +
                        "        logger.error(f'main error: {e}')\n" +
                        "        return f\"ERROR:{type(e).__name__}:{str(e)}\"\n\n" +

                        "if __name__ == '__main__':\n" +
                        "    params = json.loads(sys.argv[1]) if len(sys.argv) > 1 else {}\n" +
                        "    answer = main(params)\n" +
                        "    print(answer)\n"
        );

        return fullScript.toString();
    }

}
