package com.core.service;

import com.core.domain.AnswerKey;
import com.core.domain.Option;
import com.core.domain.Question;

public interface AnswerKeyService {

	public boolean isCorrectAnswer(Long questionId, Option Answer);

	public AnswerKey getAnswerKey(Question question);

}
