package com.chinatelecom.aigc.evaluate.listener;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;

import org.springframework.core.io.ClassPathResource;

/**
 * PDF页码监听器
 */
public class PageNumberingEventListener extends PdfPageEventHelper {
    private final BaseFont baseFont;
    private final com.itextpdf.text.Font chineseFont;

    public PageNumberingEventListener() throws DocumentException, IOException {
        baseFont = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
        chineseFont = new com.itextpdf.text.Font(baseFont, 9, com.itextpdf.text.Font.NORMAL, new BaseColor(101, 159, 101));
    }

    @Override
    public void onEndPage(PdfWriter writer, Document document) {
        //addPatternBackground(writer, document);
        addPageNumber(writer, document);
    }

    private void addGradientBackground(PdfWriter writer, Document document) {
        PdfContentByte canvas = writer.getDirectContentUnder();
        Rectangle pageSize = document.getPageSize();

        float x = pageSize.getLeft();
        float y = pageSize.getBottom();
        float width = pageSize.getWidth();
        float height = pageSize.getHeight();

        // 渐变蓝色：浅蓝到淡蓝
        PdfShading axial = PdfShading.simpleAxial(
                writer,
                x, y,
                x, y + height,
                new BaseColor(230, 242, 255),  // #E6F2FF 极浅蓝
                new BaseColor(179, 215, 255)   // #B3D7FF 淡蓝
        );
        PdfShadingPattern pattern = new PdfShadingPattern(axial);
        canvas.setShadingFill(pattern);
        canvas.rectangle(x, y, width, height);
        canvas.fill();
    }



    private void addPatternBackground(PdfWriter writer, Document document) {
        try {
            PdfContentByte canvas = writer.getDirectContentUnder();
            Rectangle pageSize = document.getPageSize();

            // 使用 Spring 方式加载资源
            ClassPathResource resource = new ClassPathResource("background/pattern.png");

            if (!resource.exists()) {
                throw new FileNotFoundException("背景图片资源不存在：background/pattern.png");
            }

            byte[] bytes = readAllBytes(resource.getInputStream());
            Image background = Image.getInstance(bytes);

            background.setAbsolutePosition(pageSize.getLeft(), pageSize.getBottom());
            background.scaleAbsolute(pageSize.getWidth(), pageSize.getHeight());

            canvas.addImage(background);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }


    private void addPageNumber(PdfWriter writer, Document document) {
        PdfPTable table = new PdfPTable(1);
        float tableWidth = document.right() - document.left();
        table.setTotalWidth(tableWidth);
        table.setHorizontalAlignment(Element.ALIGN_CENTER);

        PdfPCell cell = new PdfPCell(new Paragraph(String.format("-  %d  -", writer.getPageNumber()), chineseFont));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);

        float footerYPosition = document.bottom() - 20;
        table.writeSelectedRows(0, -1, document.left(), footerYPosition, writer.getDirectContent());
    }
}
