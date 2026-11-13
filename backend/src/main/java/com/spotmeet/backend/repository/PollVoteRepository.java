package com.spotmeet.backend.repository;

import com.spotmeet.backend.model.PollVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    List<PollVote> findByPollIdInOrderByVotedAtAsc(Collection<Long> pollIds);

    boolean existsByPollIdAndUserId(Long pollId, Long userId);
}
