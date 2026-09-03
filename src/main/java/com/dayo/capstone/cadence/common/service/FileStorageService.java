package com.dayo.capstone.cadence.common.service;

/**
 * Genuinely cross-module — User (profile images), Material (Section 4.1),
 * and Submission (Section 7's file-upload path) all depend on this.
 * Interface-first so the provider (currently Cloudinary) can be swapped
 * later without touching any calling module.
 */
public interface FileStorageService {
    String upload(byte[] fileBytes, String originalFilename, String folder);
    void delete(String fileUrl);
}