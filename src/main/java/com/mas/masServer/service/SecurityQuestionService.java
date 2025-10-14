package com.mas.masServer.service;

import com.mas.masServer.entity.SecurityQuestion;
import com.mas.masServer.repository.SecurityQuestionRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class SecurityQuestionService {

    @Autowired
    private SecurityQuestionRepository securityQuestionRepository;

    /**
     * Pre-populate security questions on application startup if the table is empty.
     */
    @PostConstruct
    public void initSecurityQuestions() {
        if (securityQuestionRepository.count() == 0) {
            List<String> questions = Arrays.asList(
                "What was the name of your first pet?",
                "What is your mother's maiden name?",
                "What was the name of your elementary school?",
                "What is the name of your favorite teacher?",
                "What was your childhood nickname?",
                "In what city were you born?",
                "What is the name of your favorite book?",
                "What was the make of your first car?",
                "What is your favorite movie?",
                "What was the name of your first friend?"
            );

            for (String questionText : questions) {
                SecurityQuestion question = new SecurityQuestion();
                question.setQuestionText(questionText);
                securityQuestionRepository.save(question);
            }
        }
    }

    /**
     * Fetch all security questions for frontend.
     */
    public List<SecurityQuestion> getAllSecurityQuestions() {
        return securityQuestionRepository.findAll();
    }
}