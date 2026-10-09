package com.qbili.data.remote.api

import com.qbili.data.remote.dto.BiliResponse
import com.qbili.data.remote.dto.SearchSquareDataDto
import com.qbili.data.remote.dto.SearchTypeDataDto
import com.qbili.data.remote.dto.SuggestResponseDto
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SearchApi {

    /**
     * 分类搜索。
     *
     * 关于风控：这个接口对「像不像真的网页搜索」很敏感。实测只要补上
     * [platform]=pc、[webLocation]=1430654，并把 Referer/Origin 指向
     * search.bilibili.com，多词关键词就不再触发 Gaia 风控（`v_voucher`）；
     * 缺了它们、用 www.bilibili.com 作 Referer 时必定被拦。
     * 配合 buvid 激活（见 AccountApi.activateBuvid）后更稳。
     *
     * @param searchType video / bili_user / live_room / media_bangumi / media_ft / article
     * @param order      仅 video 有效：totalrank / click / pubdate / dm / stow / scores
     * @param duration   仅 video 有效：0 全部 1 <10min 2 10-30min 3 30-60min 4 >60min
     * @param tids       仅 video 有效：主分区 id，0 为全部
     */
    @GET("x/web-interface/wbi/search/type")
    suspend fun searchByType(
        @Query("search_type") searchType: String,
        @Query("keyword") keyword: String,
        @Query("order") order: String?,
        @Query("duration") duration: Int?,
        @Query("tids") tids: Int?,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("platform") platform: String,
        @Query("web_location") webLocation: String,
        /** Gaia 风控验证通过后拿到的凭据，没有时传 null，Retrofit 会自动省略 */
        @Query("gaia_vtoken") gaiaVtoken: String?,
        /** 必须是 search.bilibili.com 下对应搜索类型的地址，否则易被风控 */
        @Header("Referer") referer: String,
        @Header("Origin") origin: String,
    ): BiliResponse<SearchTypeDataDto>

    /** 热搜榜 */
    @GET("x/web-interface/wbi/search/square")
    suspend fun searchSquare(
        @Query("limit") limit: Int,
        @Query("platform") platform: String,
    ): BiliResponse<SearchSquareDataDto>
}

/**
 * 搜索建议在 s.search.bilibili.com，且返回结构不带标准 data 包装，
 * 所以单独一个 Retrofit 实例 + 独立 DTO。
 */
interface SuggestApi {

    @GET("main/suggest")
    suspend fun suggest(
        @Query("term") term: String,
        @Query("main_ver") mainVer: String,
        @Header("Referer") referer: String,
    ): SuggestResponseDto
}
