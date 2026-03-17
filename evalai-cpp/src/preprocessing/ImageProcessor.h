#pragma once
#include <opencv2/opencv.hpp>
#include <string>
#include <vector>

/**
 * Main image processor — coordinates all preprocessing steps.
 * Enhances, deskews, and anonymizes answer sheet page images.
 */
class ImageProcessor {
public:
    struct ProcessResult {
        bool success;
        std::string cleanedPath;
        float skewAngle;
        bool anonymized;
        std::string errorMessage;
    };

    /**
     * Processes a single raw page image.
     * Steps: enhance → deskew → anonymize → save
     *
     * @param rawImagePath    path to the raw input image
     * @param cleanedImagePath path where processed image should be saved
     * @param pageNumber      page number (anonymization only on page 1)
     * @return ProcessResult with status and metadata
     */
    static ProcessResult processPage(
        const std::string& rawImagePath,
        const std::string& cleanedImagePath,
        int pageNumber
    );

private:
    static cv::Mat enhanceImage(const cv::Mat& image);
    static cv::Mat toGrayscale(const cv::Mat& image);
    static cv::Mat applyThreshold(const cv::Mat& gray);
    static cv::Mat denoise(const cv::Mat& image);
};