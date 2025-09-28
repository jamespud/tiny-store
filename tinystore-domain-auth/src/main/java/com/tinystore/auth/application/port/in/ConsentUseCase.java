package com.tinystore.auth.application.port.in;

import com.tinystore.auth.application.dto.ConsentView;
import com.tinystore.auth.application.dto.LoadConsentCommand;

public interface ConsentUseCase {

	ConsentView loadConsent(LoadConsentCommand command);
}