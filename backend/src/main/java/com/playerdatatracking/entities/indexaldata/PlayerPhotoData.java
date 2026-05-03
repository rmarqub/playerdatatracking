package com.playerdatatracking.entities.indexaldata;

import java.time.LocalDateTime;

public interface PlayerPhotoData {
    byte[] getPhoto();
    String getPhotoContentType();
    LocalDateTime getPhotoUpdatedAt();
}
