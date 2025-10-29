package com.playerdatatracking.repositories.indexaldata;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.playerdatatracking.entities.indexaldata.Transfer;

@Repository
public interface TransferRepository extends JpaRepository<Transfer, Long> {

    List<Transfer> findByPlayer(Long player);
    List<Transfer> findByInPlayer(Long inPlayer);
    List<Transfer> findByOutPlayer(Long outPlayer);

    List<Transfer> findByInPlayerAndOutPlayer(Long inPlayer, Long outPlayer);
}