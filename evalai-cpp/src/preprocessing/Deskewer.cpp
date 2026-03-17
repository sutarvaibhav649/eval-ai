#include "Deskewer.h"
#include <opencv2/opencv.hpp>
#include <cmath>
#include <vector>

float Deskewer::detectSkewAngle(const cv::Mat& image) {
    // Apply edge detection
    cv::Mat edges;
    cv::Canny(image, edges, 50, 150, 3);

    // Detect lines using Hough Transform
    std::vector<cv::Vec2f> lines;
    cv::HoughLines(edges, lines, 1, CV_PI / 180, 100);

    if (lines.empty()) {
        return 0.0f;
    }

    // Calculate average angle from detected lines
    double angleSum = 0.0;
    int count = 0;

    for (const auto& line : lines) {
        double angle = line[1] * 180.0 / CV_PI;

        // Only consider near-horizontal lines (text lines)
        if (angle < 10 || angle > 170) {
            if (angle > 90) angle -= 180;
            angleSum += angle;
            count++;
        }
    }

    if (count == 0) return 0.0f;

    float avgAngle = static_cast<float>(angleSum / count);

    // Clamp to reasonable range — don't over-correct
    if (std::abs(avgAngle) > 45.0f) return 0.0f;

    return avgAngle;
}

cv::Mat Deskewer::correctSkew(const cv::Mat& image, float angle) {
    // Get image center
    cv::Point2f center(
        static_cast<float>(image.cols) / 2.0f,
        static_cast<float>(image.rows) / 2.0f
    );

    // Get rotation matrix
    cv::Mat rotationMatrix = cv::getRotationMatrix2D(center, angle, 1.0);

    // Apply rotation with white background
    cv::Mat rotated;
    cv::warpAffine(
        image, rotated, rotationMatrix,
        image.size(),
        cv::INTER_LINEAR,
        cv::BORDER_CONSTANT,
        cv::Scalar(255, 255, 255)
    );

    return rotated;
}