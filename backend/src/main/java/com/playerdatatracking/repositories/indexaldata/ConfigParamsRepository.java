package com.playerdatatracking.repositories.indexaldata;

import org.springframework.data.jpa.repository.JpaRepository;

import com.playerdatatracking.entities.indexaldata.ConfigParams;

public interface ConfigParamsRepository extends JpaRepository<ConfigParams, Long> {

	ConfigParams findByKey(String Key);
}
