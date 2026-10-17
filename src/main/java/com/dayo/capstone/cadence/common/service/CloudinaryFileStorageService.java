package com.dayo.capstone.cadence.common.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudinaryFileStorageService implements FileStorageService {

    private final Cloudinary cloudinary;

    @Override
    public String upload(byte[] fileBytes, String originalFilename, String folder) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(fileBytes, ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "auto", // auto-detects image vs raw document
                    "public_id", java.util.UUID.randomUUID().toString(),
                    "use_filename", false
            ));
            String url = result.get("secure_url").toString();
            log.info("File uploaded: folder={}, originalFilename={}, url={}", folder, originalFilename, url);
            return url;
        } catch (Exception e) {
            log.error("File upload failed: originalFilename={}", originalFilename, e);
            throw new RuntimeException("File upload failed", e);
        }
    }

    @Override
    public void delete(String fileUrl) {
        try {
            String publicId = extractPublicId(fileUrl);
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("File deleted: url={}", fileUrl);
        } catch (Exception e) {
            log.warn("File deletion failed (non-fatal): url={}", fileUrl, e);
        }
    }

    private String extractPublicId(String fileUrl) {
        String[] parts = fileUrl.split("/");
        String lastPart = parts[parts.length - 1];
        return lastPart.substring(0, lastPart.lastIndexOf('.'));
    }
}