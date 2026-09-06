package com.belajarbahasa.app.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Kontrak endpoint HARUS sinkron dengan backend Rust:
 * - backend/src/handlers/folders.rs
 * - backend/src/handlers/materi.rs
 *
 * Kalau parentId null, Retrofit otomatis TIDAK mengirim query param
 * (persis seperti backend mengharapkan Option<Uuid> = None -> ambil folder root).
 */
interface ApiService {

    @GET("api/folders")
    suspend fun listFolders(@Query("parent_id") parentId: String? = null): List<FolderListItem>

    @GET("api/folders/{id}")
    suspend fun getFolder(@Path("id") id: String): FolderListItem

    @POST("api/folders")
    suspend fun createFolder(@Body body: CreateFolderRequest): FolderListItem

    @DELETE("api/folders/{id}")
    suspend fun deleteFolder(@Path("id") id: String): DeletedResponse

    @GET("api/folders/{folderId}/materi")
    suspend fun listMateriByFolder(@Path("folderId") folderId: String): List<Materi>

    @Multipart
    @POST("api/materi")
    suspend fun createMateri(
        @Part("judul") judul: RequestBody,
        @Part("folder_id") folderId: RequestBody,
        @Part file: MultipartBody.Part
    ): Materi

    @DELETE("api/materi/{id}")
    suspend fun deleteMateri(@Path("id") id: String): DeletedResponse
}
