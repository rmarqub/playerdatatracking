package com.playerdatatracking.repositories.indexaldata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.playerdatatracking.entities.indexaldata.TipoTorneo;

@Repository
public interface TipoTorneoRepository extends JpaRepository<TipoTorneo, Long> {

	
	TipoTorneo findByTipo(String Tipo);
}
