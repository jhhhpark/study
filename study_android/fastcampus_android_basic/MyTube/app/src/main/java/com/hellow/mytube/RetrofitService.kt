package com.hellow.mytube

import retrofit2.Call
import retrofit2.http.GET

interface RetrofitService {
    @GET("youtube/list/")
    fun getYoutubeList(): Call<ArrayList<Youtube>>
}