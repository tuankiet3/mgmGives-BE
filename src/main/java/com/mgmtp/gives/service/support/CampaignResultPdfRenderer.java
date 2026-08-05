package com.mgmtp.gives.service.support;

import com.mgmtp.gives.common.ErrorCode;
import com.mgmtp.gives.common.MailProps;
import com.mgmtp.gives.dto.campaign_spending.CampaignSpendingListResponse;
import com.mgmtp.gives.entity.Campaign;
import com.mgmtp.gives.entity.CampaignMedia;
import com.mgmtp.gives.entity.CampaignTask;
import com.mgmtp.gives.enums.TaskStatus;
import com.mgmtp.gives.exception.AppException;
import com.mgmtp.gives.repository.CampaignMediaRepository;
import com.mgmtp.gives.repository.CampaignTaskRepository;
import com.mgmtp.gives.service.CampaignSpendingService;
import com.mgmtp.gives.specification.CampaignTaskSpecifications;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignResultPdfRenderer {

    private static final DateTimeFormatter REPORT_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final String PDF_FONT_FAMILY = "Noto Sans";
    private static final int PDF_IMAGE_MAX_DIMENSION = 640;
    private static final float PDF_IMAGE_JPEG_QUALITY = 0.7f;

    private final TemplateEngine templateEngine;
    private final CampaignMediaRepository campaignMediaRepository;
    private final CampaignTaskRepository campaignTaskRepository;
    private final CampaignSpendingService campaignSpendingService;
    private final MailProps mailProps;

    @Value("${app.media.upload-dir}")
    private String uploadDir;

    public byte[] render(Campaign campaign) {
        long totalRaised = campaign.getFinalAmountRaised() != null ? campaign.getFinalAmountRaised() : 0L;
        double goalPercent = CampaignResultMetrics.calculateGoalPercent(campaign.getTarget(), totalRaised);
        TaskCounts taskCounts = computeActiveTaskCounts(campaign.getId());

        Context context = new Context();
        context.setVariable("campaignName", campaign.getTitle());
        context.setVariable("resultSummary", campaign.getResultSummary());
        context.setVariable("itemsSummary", campaign.getItemsSummary());
        context.setVariable("acknowledgements", campaign.getAcknowledgements());
        context.setVariable("taskSummary", campaign.getTaskSummary());
        context.setVariable("taskCount", taskCounts.total());
        context.setVariable("completedTaskCount", taskCounts.completed());
        context.setVariable("publishedByName", campaign.getResultPublishedBy() != null
                ? campaign.getResultPublishedBy().getFullName()
                : null);
        context.setVariable("publishedAt", campaign.getResultPublishedAt() != null
                ? campaign.getResultPublishedAt().format(REPORT_DATE_FORMAT)
                : null);
        context.setVariable("totalRaised", NumberFormat.getNumberInstance(Locale.US).format(totalRaised));
        context.setVariable("donorCount", campaign.getFinalDonorCount() != null ? campaign.getFinalDonorCount() : 0L);
        context.setVariable(
                "volunteerCount", campaign.getFinalVolunteerCount() != null ? campaign.getFinalVolunteerCount() : 0L);
        context.setVariable("goalPercent", String.format(Locale.US, "%.0f", goalPercent));
        context.setVariable("reportLink", buildReportLink(campaign.getId()));
        context.setVariable("galleryMedia", buildGalleryMedia(campaign));

        CampaignSpendingListResponse spending =
                campaignSpendingService.getSpendingsByCampaign(campaign.getId(), totalRaised);
        context.setVariable("spendingItems", buildPdfSpendingItems(spending));
        context.setVariable("totalSpent", NumberFormat.getNumberInstance(Locale.US).format(spending.totalSpent()));
        context.setVariable(
                "remainingFunds", NumberFormat.getNumberInstance(Locale.US).format(spending.remainingFunds()));

        String html = templateEngine.process("final-report-pdf", context);
        return renderHtml(campaign.getId(), html);
    }

    private byte[] renderHtml(Long campaignId, String html) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(
                    () -> getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf"),
                    PDF_FONT_FAMILY,
                    400,
                    BaseRendererBuilder.FontStyle.NORMAL,
                    true);
            builder.useFont(
                    () -> getClass().getResourceAsStream("/fonts/NotoSans-Bold.ttf"),
                    PDF_FONT_FAMILY,
                    700,
                    BaseRendererBuilder.FontStyle.NORMAL,
                    true);
            builder.withHtmlContent(html, null);
            builder.toStream(outputStream);
            builder.run();
            return outputStream.toByteArray();
        } catch (IOException exception) {
            log.error("Failed to render final report PDF: campaignId={}", campaignId, exception);
            throw new AppException(ErrorCode.UNCATEGORIZED_ERROR, "Failed to generate final report PDF");
        }
    }

    private String buildReportLink(Long campaignId) {
        return UriComponentsBuilder.fromUriString(mailProps.getFrontendUrl())
                .pathSegment("campaigns", campaignId.toString(), "result")
                .toUriString();
    }

    private List<PdfMediaItem> buildGalleryMedia(Campaign campaign) {
        return campaignMediaRepository.findByCampaignIdAndDeletedAtIsNull(campaign.getId()).stream()
                .filter(media -> !media.isCover())
                .map(media -> new PdfMediaItem(
                        "VIDEO".equalsIgnoreCase(media.getMediaType()) ? null : buildImageDataUri(media.getUrl()),
                        "VIDEO".equalsIgnoreCase(media.getMediaType())))
                .filter(item -> item.isVideo() || item.url() != null)
                .toList();
    }

    private String buildImageDataUri(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        File file = Paths.get(uploadDir).resolve(filename).toFile();
        if (!file.exists()) {
            return null;
        }
        try {
            BufferedImage original = ImageIO.read(file);
            if (original == null) {
                return null;
            }
            BufferedImage resized = resizeToMaxDimension(original, PDF_IMAGE_MAX_DIMENSION);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            writeJpeg(resized, buffer, PDF_IMAGE_JPEG_QUALITY);
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(buffer.toByteArray());
        } catch (IOException exception) {
            log.warn("Failed to load gallery image for final report PDF: file={}", filename, exception);
            return null;
        }
    }

    private static BufferedImage resizeToMaxDimension(BufferedImage original, int maxDimension) {
        int width = original.getWidth();
        int height = original.getHeight();
        if (Math.max(width, height) <= maxDimension) {
            return toOpaqueRgb(original);
        }
        double scale = (double) maxDimension / Math.max(width, height);
        int newWidth = Math.max(1, (int) Math.round(width * scale));
        int newHeight = Math.max(1, (int) Math.round(height * scale));
        Image scaledImage = original.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(scaledImage, 0, 0, Color.WHITE, null);
        graphics.dispose();
        return resized;
    }

    private static BufferedImage toOpaqueRgb(BufferedImage original) {
        if (original.getType() == BufferedImage.TYPE_INT_RGB) {
            return original;
        }
        BufferedImage rgb = new BufferedImage(original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.drawImage(original, 0, 0, Color.WHITE, null);
        graphics.dispose();
        return rgb;
    }

    private static void writeJpeg(BufferedImage image, ByteArrayOutputStream output, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam parameters = writer.getDefaultWriteParam();
        parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parameters.setCompressionQuality(quality);
        try (ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }

    private List<PdfSpendingItem> buildPdfSpendingItems(CampaignSpendingListResponse spending) {
        return spending.items().stream()
                .map(item -> new PdfSpendingItem(
                        item.description(),
                        NumberFormat.getNumberInstance(Locale.US).format(item.amount()),
                        item.spentAt().format(REPORT_DATE_FORMAT),
                        item.photos().isEmpty() ? null : buildImageDataUri(item.photos().getFirst().getUrl())))
                .toList();
    }

    private TaskCounts computeActiveTaskCounts(Long campaignId) {
        Specification<CampaignTask> activeTaskSpec = CampaignTaskSpecifications.hasCampaignId(campaignId)
                .and(CampaignTaskSpecifications.isNotDeleted())
                .and(CampaignTaskSpecifications.hasIsArchived(false));
        long total = campaignTaskRepository.count(activeTaskSpec);
        long completed = campaignTaskRepository.count(
                activeTaskSpec.and(CampaignTaskSpecifications.hasStatus(TaskStatus.DONE)));
        return new TaskCounts(total, completed);
    }

    private record PdfMediaItem(String url, boolean isVideo) {
    }

    private record PdfSpendingItem(String description, String amount, String spentAt, String photoUrl) {
    }

    private record TaskCounts(long total, long completed) {
    }
}
