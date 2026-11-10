package com.example.guardband.testing

import com.example.guardband.data.model.ConnectedWearer
import com.example.guardband.data.repository.WearerConnectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * [WearerConnectionRepository] whose state a test sets by hand.
 * All suspend functions succeed by default.
 */
class FakeWearerConnectionRepository : WearerConnectionRepository {

    var generateResult: Result<String> = Result.success("FAKE01")
    var redeemResult: Result<Unit> = Result.success(Unit)
    var removeWatcherResult: Result<Unit> = Result.success(Unit)
    var stopWatchingResult: Result<Unit> = Result.success(Unit)

    val wearers = MutableStateFlow<Result<List<ConnectedWearer>>>(Result.success(emptyList()))

    override suspend fun generateInviteCode(): Result<String> = generateResult

    override suspend fun redeemInviteCode(code: String): Result<Unit> = redeemResult

    override fun observeConnectedWearers(): Flow<Result<List<ConnectedWearer>>> = wearers

    override suspend fun removeWatcher(watcherUid: String): Result<Unit> = removeWatcherResult

    override suspend fun stopWatching(wearerUid: String): Result<Unit> = stopWatchingResult
}
