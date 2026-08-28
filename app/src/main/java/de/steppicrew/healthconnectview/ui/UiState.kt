package de.steppicrew.healthconnectview.ui

/**
 * Screen state.
 *
 * [Empty] and [NoPermission] are deliberately distinct: "you have not granted access" and
 * "access is granted but there is nothing here" need different explanations and different
 * actions, and conflating them makes the app look broken when it is merely unauthorised.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data object NoPermission : UiState<Nothing>
    data object Empty : UiState<Nothing>
    data class Error(val message: String) : UiState<Nothing>
    data class Data<T>(val value: T) : UiState<T>
}
