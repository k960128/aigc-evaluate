package com.kant.springai.agent.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * * 文档解析服务
 */
@Slf4j
@Service
public class DocumentParseService {
    /**
     * 支持的文档扩展名
     */
    private static final Set<String> SUPPORT_EXTENSIONS = Set.of("pdf", "doc", "docx");
    /**
     * 读取附件中的文字内容
     * @param multipartFile 附件
     * @return 文字内容
     */
    public String extractContent(MultipartFile multipartFile) {
        validateFile(multipartFile);

        String originalFilename = Objects.requireNonNull(multipartFile.getOriginalFilename());
        String extension = getExtension(originalFilename);
        log.info("开始解析文档：{}", originalFilename);

        try {
            String documentContent = switch (extension) {
                case "pdf" -> extractPdfContent(multipartFile);
                case "doc" -> extractDocContent(multipartFile);
                case "docx" -> extractDocxContent(multipartFile);
                default -> throw new IllegalArgumentException("仅支持 PDF、Word 文档");
            };

            if (StringUtils.isBlank(documentContent)) {
                throw new IllegalArgumentException("文档没有识别到有效文字内容");
            }
            log.info("文档解析成功：\n{}", documentContent);
            return documentContent.trim();
        } catch (IOException e) {
            log.error("读取文档失败：{}", originalFilename, e);
            throw new RuntimeException("读取文档失败", e);
        }
    }

    /**
     * 校验上传文件
     */
    private void validateFile(MultipartFile multipartFile) {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("请上传文档");
        }

        String originalFilename = multipartFile.getOriginalFilename();
        if (StringUtils.isBlank(originalFilename)) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        String extension = getExtension(originalFilename);
        if (!SUPPORT_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("仅支持 PDF、Word 文档");
        }
    }

    /**
     * 读取 PDF 文本
     */
    private String extractPdfContent(MultipartFile multipartFile) {
        PdfDocumentReaderConfig pdfDocumentReaderConfig = PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                .withPagesPerDocument(1)
                .build();

        PagePdfDocumentReader documentReader = new PagePdfDocumentReader(
                multipartFile.getResource(), pdfDocumentReaderConfig);

        List<Document> documents = documentReader.read();

        return documents.stream()
                .map(Document::getText)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 读取老版本 Word 文本
     */
    private String extractDocContent(MultipartFile multipartFile) throws IOException {
        try (InputStream inputStream = multipartFile.getInputStream();
             HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    /**
     * 读取新版 Word 文本
     */
    private String extractDocxContent(MultipartFile multipartFile) throws IOException {
        try (InputStream inputStream = multipartFile.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    /**
     * 获取文件扩展名
     */
    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf(".");
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new IllegalArgumentException("文件类型不正确");
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }
}
