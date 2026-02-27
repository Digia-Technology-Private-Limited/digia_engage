//package com.digia.digiaui.network
//
//import okhttp3.MultipartBody
//import okhttp3.RequestBody
//import okhttp3.ResponseBody
//import retrofit2.Call
//import retrofit2.http.*
//
//interface RetrofitService {
//
//    @HTTP(method = "GET", path = "", hasBody = false)
//    fun requestGet(@Url url: String, @HeaderMap headers: Map<String, String>): Call<ResponseBody>
//
//    @HTTP(method = "POST", path = "", hasBody = true)
//    fun requestPost(@Url url: String, @HeaderMap headers: Map<String, String>, @Body body: RequestBody?): Call<ResponseBody>
//
//    @HTTP(method = "PUT", path = "", hasBody = true)
//    fun requestPut(@Url url: String, @HeaderMap headers: Map<String, String>, @Body body: RequestBody?): Call<ResponseBody>
//
//    @HTTP(method = "DELETE", path = "", hasBody = false)
//    fun requestDelete(@Url url: String, @HeaderMap headers: Map<String, String>): Call<ResponseBody>
//
//    @HTTP(method = "PATCH", path = "", hasBody = true)
//    fun requestPatch(@Url url: String, @HeaderMap headers: Map<String, String>, @Body body: RequestBody?): Call<ResponseBody>
//
//    @Multipart
//    @POST
//    fun uploadMultipart(@Url url: String, @HeaderMap headers: Map<String, String>, @Part parts: List<MultipartBody.Part>): Call<ResponseBody>
//}
