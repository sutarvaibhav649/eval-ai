#pragma once
#include <opencv2/opencv.hpp>

/**
 * Detects and corrects skew in scanned answer sheet images.
 * Uses Hough Line Transform to find dominant text angle.
 */
class Deskewer {
public:
    /**
     * Detects the skew angle of a document image.
     * @param image grayscale input image
     * @return skew angle in degrees (negative = counter-clockwise)
     */
    static float detectSkewAngle(const cv::Mat& image);

    /**
     * Rotates image to correct detected skew.
     * @param image  input image
     * @param angle  skew angle to correct
     * @return deskewed image
     */
    static cv::Mat correctSkew(const cv::Mat& image, float angle);
};