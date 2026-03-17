#include "ImageProcessor.h"
#include "Deskewer.h"
#include "Anonymizer.h"
#include <filesystem>
#include <iostream>

ImageProcessor::ProcessResult ImageProcessor::processPage(
    const std::string& rawImagePath,
    const std::string& cleanedImagePath,
    int pageNumber)
{
    ProcessResult result;
    result.success = false;
    result.skewAngle = 0.0f;
    result.anonymized = false;

    // Step 1 - Load image
    cv::Mat image = cv::imread(rawImagePath, cv::IMREAD_COLOR);
    if (image.empty()) {
        result.errorMessage = "Failed to load image: " + rawImagePath;
        return result;
    }

    // Step 2 - Convert to grayscale
    cv::Mat gray = toGrayscale(image);

    // Step 3 - Detect and correct skew
    float skewAngle = Deskewer::detectSkewAngle(gray);
    result.skewAngle = skewAngle;

    cv::Mat deskewed = image;
    if (skewAngle > 0.5f || skewAngle < -0.5f) {
        deskewed = Deskewer::correctSkew(image, skewAngle);
    }

    // Step 4 - Enhance image
    cv::Mat enhanced = enhanceImage(deskewed);

    // Step 5 - Anonymize page 1 only
    result.anonymized = Anonymizer::anonymize(enhanced, pageNumber);

    // Step 6 - Create output directory
    std::filesystem::path outputPath(cleanedImagePath);
    std::filesystem::create_directories(outputPath.parent_path());

    // Step 7 - Save cleaned image
    bool saved = cv::imwrite(cleanedImagePath, enhanced);
    if (!saved) {
        result.errorMessage = "Failed to save image: " + cleanedImagePath;
        return result;
    }

    result.success = true;
    result.cleanedPath = cleanedImagePath;
    return result;
}

cv::Mat ImageProcessor::enhanceImage(const cv::Mat& image)
{
    cv::Mat lab;
    cv::cvtColor(image, lab, cv::COLOR_BGR2Lab);

    std::vector<cv::Mat> labChannels;
    cv::split(lab, labChannels);

    cv::Ptr<cv::CLAHE> clahe = cv::createCLAHE(2.0, cv::Size(8, 8));
    clahe->apply(labChannels[0], labChannels[0]);

    cv::Mat enhanced;
    cv::merge(labChannels, lab);
    cv::cvtColor(lab, enhanced, cv::COLOR_Lab2BGR);

    enhanced = denoise(enhanced);
    return enhanced;
}

cv::Mat ImageProcessor::toGrayscale(const cv::Mat& image)
{
    cv::Mat gray;
    cv::cvtColor(image, gray, cv::COLOR_BGR2GRAY);
    return gray;
}

cv::Mat ImageProcessor::applyThreshold(const cv::Mat& gray)
{
    cv::Mat binary;
    cv::adaptiveThreshold(
        gray, binary, 255,
        cv::ADAPTIVE_THRESH_GAUSSIAN_C,
        cv::THRESH_BINARY, 11, 2
    );
    return binary;
}

cv::Mat ImageProcessor::denoise(const cv::Mat& image)
{
    cv::Mat denoised;
    cv::fastNlMeansDenoisingColored(image, denoised, 3, 3, 7, 21);
    return denoised;
}