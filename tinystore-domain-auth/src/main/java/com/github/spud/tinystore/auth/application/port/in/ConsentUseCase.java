package com.github.spud.tinystore.auth.application.port.in;

import com.github.spud.tinystore.auth.application.dto.ConsentView;
import com.github.spud.tinystore.auth.application.dto.LoadConsentCommand;

public interface ConsentUseCase {

  ConsentView loadConsent(LoadConsentCommand command);
}