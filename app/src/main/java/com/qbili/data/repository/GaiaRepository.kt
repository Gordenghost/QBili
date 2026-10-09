package com.qbili.data.repository

import com.qbili.core.BiliApiException
import com.qbili.data.remote.api.GaiaApi
import com.qbili.data.remote.dto.requireData
import com.qbili.data.session.GaiaTokenStore
import com.qbili.domain.model.GaiaChallenge

class GaiaRepository(
    private val gaiaApi: GaiaApi,
    private val tokenStore: GaiaTokenStore,
) {

    /**
     * 用 v_voucher 注册一次风控挑战。
     *
     * 只支持 geetest 类型；手机号、实名、活体检测这类强验证必须在官方端完成，
     * 遇到时直接把情况说清楚，不要让用户对着一个转圈等下去。
     */
    suspend fun register(voucher: String): GaiaChallenge {
        val data = gaiaApi.register(voucher).requireData()
        val geetest = data.geetest
        if (data.type != TYPE_GEETEST || geetest == null ||
            geetest.gt.isBlank() || geetest.challenge.isBlank()
        ) {
            throw BiliApiException(
                -1,
                "B 站要求的验证方式是「${data.type.ifBlank { "未知" }}」，" +
                    "需要在官方 App 或网页端完成后再回来重试",
            )
        }
        return GaiaChallenge(
            token = data.token,
            gt = geetest.gt,
            challenge = geetest.challenge,
        )
    }

    /** @return 是否验证通过；通过时同时把 grisk_id 存进 [GaiaTokenStore] */
    suspend fun validate(
        token: String,
        challenge: String,
        validate: String,
        seccode: String,
    ): Boolean {
        val data = gaiaApi.validate(
            token = token,
            challenge = challenge,
            validate = validate,
            seccode = seccode,
        ).requireData()

        if (data.passed) tokenStore.update(data.griskId)
        return data.passed
    }

    private companion object {
        const val TYPE_GEETEST = "geetest"
    }
}
