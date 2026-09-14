package com.teamcatalyst.supplychain.repository;

import com.teamcatalyst.supplychain.model.DisruptionEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisruptionEventRepository extends JpaRepository<DisruptionEvent, Long> {

}
