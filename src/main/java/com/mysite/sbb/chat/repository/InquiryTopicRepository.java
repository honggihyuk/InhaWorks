package com.mysite.sbb.chat.repository;

import com.mysite.sbb.chat.entity.InquiryTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface InquiryTopicRepository extends JpaRepository<InquiryTopic, Long> {

    @Query("select distinct t from InquiryTopic t left join fetch t.handlers order by t.id asc")
    List<InquiryTopic> findAllWithHandlers();
}
