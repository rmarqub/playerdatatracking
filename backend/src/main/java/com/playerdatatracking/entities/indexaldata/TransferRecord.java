package com.playerdatatracking.entities.indexaldata;

import java.time.LocalDate;

public class TransferRecord {
    final LocalDate date;
    final Long inId;
    final Long outId;

    public TransferRecord(LocalDate date, Long inId, Long outId) {
        this.date = date;
        this.inId = inId;
        this.outId = outId;
    }

	public LocalDate getDate() {
		return date;
	}

	public Long getInId() {
		return inId;
	}

	public Long getOutId() {
		return outId;
	}

    
    
}
