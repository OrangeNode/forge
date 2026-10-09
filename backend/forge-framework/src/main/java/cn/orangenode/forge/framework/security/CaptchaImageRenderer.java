package cn.orangenode.forge.framework.security;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.QuadCurve2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.SecureRandom;

import javax.imageio.ImageIO;

/**
 * 图形验证码图片绘制。
 *
 * <p>逐个字符随机旋转、偏移与着色，再叠加干扰曲线和噪点；只依赖 JDK 自带的 AWT 与 ImageIO，
 * 运行在无界面（headless）环境，部署镜像需要至少一种系统字体（eclipse-temurin 镜像自带 DejaVu）。</p>
 */
final class CaptchaImageRenderer {

    /**
     * 干扰曲线条数。
     */
    private static final int NOISE_LINES = 5;

    /**
     * 每千像素的噪点数量。
     */
    private static final int NOISE_DOTS_PER_KILO_PIXEL = 12;

    /**
     * 随机源。
     */
    private final SecureRandom random = new SecureRandom();

    /**
     * 构造验证码图片绘制器。
     */
    CaptchaImageRenderer() {
    }

    /**
     * 把验证码文字绘制为 PNG 图片。
     *
     * @param text   验证码文字
     * @param width  图片宽度像素
     * @param height 图片高度像素
     * @return PNG 字节
     * @throws UncheckedIOException 图片编码失败时抛出
     */
    byte[] render(String text, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(new Color(244, 247, 252));
            graphics.fillRect(0, 0, width, height);
            drawNoiseLines(graphics, width, height);
            drawText(graphics, text, width, height);
            drawNoiseDots(image, width, height);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", output);
        } catch (IOException failure) {
            throw new UncheckedIOException("验证码图片编码失败", failure);
        }
        return output.toByteArray();
    }

    /**
     * 逐字符绘制文字，每个字符单独旋转与上下偏移。
     *
     * @param graphics 画笔
     * @param text     验证码文字
     * @param width    图片宽度
     * @param height   图片高度
     */
    private void drawText(Graphics2D graphics, String text, int width, int height) {
        int count = text.length();
        float cell = (float) width / (count + 1);
        int fontSize = Math.round(height * 0.72f);
        AffineTransform original = graphics.getTransform();
        for (int index = 0; index < count; index++) {
            int style = random.nextBoolean() ? Font.BOLD : Font.BOLD | Font.ITALIC;
            graphics.setFont(new Font(Font.SANS_SERIF, style, fontSize + random.nextInt(5) - 2));
            graphics.setColor(darkColor());
            double x = cell * (index + 0.5) + random.nextInt(Math.max(1, (int) (cell / 3)));
            double y = height * 0.5 + fontSize * 0.35 + random.nextInt(Math.max(1, height / 6)) - height / 12.0;
            double angle = Math.toRadians(random.nextInt(51) - 25);
            graphics.rotate(angle, x, y);
            graphics.drawString(String.valueOf(text.charAt(index)), (float) x, (float) y);
            graphics.setTransform(original);
        }
    }

    /**
     * 绘制贯穿图片的干扰曲线。
     *
     * @param graphics 画笔
     * @param width    图片宽度
     * @param height   图片高度
     */
    private void drawNoiseLines(Graphics2D graphics, int width, int height) {
        for (int index = 0; index < NOISE_LINES; index++) {
            graphics.setColor(lightColor());
            graphics.setStroke(new BasicStroke(1f + random.nextFloat() * 1.5f));
            int startY = random.nextInt(height);
            int endY = random.nextInt(height);
            int controlX = random.nextInt(width);
            int controlY = random.nextInt(height * 2) - height / 2;
            graphics.draw(new QuadCurve2D.Float(0, startY, controlX, controlY, width, endY));
        }
    }

    /**
     * 随机散布噪点。
     *
     * @param image  图片
     * @param width  图片宽度
     * @param height 图片高度
     */
    private void drawNoiseDots(BufferedImage image, int width, int height) {
        int dots = width * height * NOISE_DOTS_PER_KILO_PIXEL / 1000;
        for (int index = 0; index < dots; index++) {
            image.setRGB(random.nextInt(width), random.nextInt(height), lightColor().getRGB());
        }
    }

    /**
     * 生成用于文字的深色。
     *
     * @return 颜色
     */
    private Color darkColor() {
        return new Color(20 + random.nextInt(90), 30 + random.nextInt(90), 60 + random.nextInt(110));
    }

    /**
     * 生成用于干扰的浅色。
     *
     * @return 颜色
     */
    private Color lightColor() {
        return new Color(120 + random.nextInt(110), 120 + random.nextInt(110), 140 + random.nextInt(100));
    }
}
