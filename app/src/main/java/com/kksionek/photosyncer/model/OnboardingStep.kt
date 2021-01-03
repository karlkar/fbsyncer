package com.kksionek.photosyncer.model

sealed class OnboardingStep {

    object StepPermissions: OnboardingStep()

    object FbLogin: OnboardingStep()

    object Completed: OnboardingStep()
}
