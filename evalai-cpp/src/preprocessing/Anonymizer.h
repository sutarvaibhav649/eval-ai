#pragma once
#include <opencv2/opencv.hpp>

/**
 * Anonymizes student answer sheets by masking identifying information.
 * Blanks out the top region of page 1 where student name/PRN appears.
 * This is always applied to page 1 — cannot be disabled.
 */
class Anonymizer {
public:
    /**
     * Masks the top portion of the image where student ID info appears.
     * Replaces with white rectangle.
     *
     * @param image      input image (modified in place)
     * @param pageNumber only anonymizes page 1
     * @return true if anonymization was applied
     */
    static bool anonymize(cv::Mat& image, int pageNumber);

    // Height percentage of image to mask (top 15%)
    static constexpr float MASK_HEIGHT_RATIO = 0.15f;
};