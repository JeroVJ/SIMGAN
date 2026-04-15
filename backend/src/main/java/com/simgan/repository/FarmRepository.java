package com.simgan.repository;

import com.simgan.entity.Farm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FarmRepository extends JpaRepository<Farm, Long> {
	@Query("SELECT COUNT(f) > 0 FROM Farm f WHERE LOWER(TRIM(f.name)) = LOWER(TRIM(:name))")
	boolean existsByNormalizedName(@Param("name") String name);

    List<Farm> findByGanaderoId(Long ganaderoId);
}
