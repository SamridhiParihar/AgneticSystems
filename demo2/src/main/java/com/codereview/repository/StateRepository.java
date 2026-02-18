package com.codereview.repository;

import com.codereview.model.CodeReviewState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * JPA Repository for CodeReviewState
 *
 */
@Repository
public interface StateRepository extends JpaRepository<CodeReviewState, String> {

}