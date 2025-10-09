package com.chinatelecom.aigc.evaluate.common.util.pdf;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.FileOutputStream;
import java.io.IOException;

public class PdfUtils {

    // 生成 PDF 文件
    public static void generatePdf(String content, String filePath) throws IOException {
        Document document = new Document();
        try {
            // 创建 PDF 文件输出流
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            // 打开文档
            document.open();
            // 将报告内容添加到 PDF 中
            document.add(new Paragraph(content));
        } catch (Exception e) {
            throw new IOException("生成 PDF 文件时出错", e);
        } finally {
            // 关闭文档
            document.close();
        }
    }

    public static void generateStatisticsPdf(String filePath) throws IOException {
        Document document = new Document();
        try {
            // 创建 PDF 文件输出流
            PdfWriter.getInstance(document, new FileOutputStream(filePath));
            // 打开文档
            document.open();

        } catch (Exception e) {
            throw new IOException("生成 PDF 文件时出错", e);
        } finally {
            // 关闭文档
            document.close();
        }
    }

    public static void generateStatisticsPdf(Document document, String filePath) throws IOException {
        if (document == null || !document.isOpen()) {
            throw new IllegalArgumentException("Document 无效或未打开");
        }

        PdfWriter pdfWriter = null;
        try {
            // 创建 PdfWriter 并关联到 Document
            pdfWriter = PdfWriter.getInstance(document, new FileOutputStream(filePath));
        } catch (DocumentException e) {
            throw new IOException("生成 PDF 文件时出错", e);
        } finally {
            if (pdfWriter != null) {
                pdfWriter.close(); // 关闭 PdfWriter
            }
        }
    }
}
