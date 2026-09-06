package com.belajarbahasa.app.network

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

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

    // ===== Daftar Isi - Task 4 =====
    // Sinkron dengan backend/src/handlers/daftar_isi.rs

    @GET("api/materi/{materiId}/daftar-isi")
    suspend fun getDaftarIsi(@Path("materiId") materiId: String): List<DaftarIsiNode>

    @POST("api/materi/{materiId}/daftar-isi")
    suspend fun createBab(
        @Path("materiId") materiId: String,
        @Body body: CreateBabRequest
    ): DaftarIsiNode

    @PUT("api/daftar-isi/{id}")
    suspend fun updateBab(@Path("id") id: String, @Body body: UpdateBabRequest): DaftarIsiNode

    @DELETE("api/daftar-isi/{id}")
    suspend fun deleteBab(@Path("id") id: String): DeletedResponse

    @POST("api/materi/{materiId}/daftar-isi/import")
    suspend fun importDaftarIsi(
        @Path("materiId") materiId: String,
        @Body body: ImportDaftarIsiRequest
    ): List<DaftarIsiNode>

    @GET("api/materi/{materiId}/daftar-isi/export")
    suspend fun exportDaftarIsi(@Path("materiId") materiId: String): ExportDaftarIsiResponse

    @GET("api/daftar-isi/template")
    suspend fun getDaftarIsiTemplate(): ExportDaftarIsiResponse

    // ===== PDF Viewer, Progress & Bookmark - Task 5 =====
    // Sinkron dengan backend/src/handlers/materi.rs (get_materi_file),
    // backend/src/handlers/progress.rs, backend/src/handlers/bookmarks.rs

    /** @Streaming wajib ada supaya body tidak dibaca penuh ke memory oleh OkHttp
     * sebelum sempat kita simpan ke file cache lokal (lihat PdfViewerScreen.kt). */
    @Streaming
    @GET("api/materi/{id}/file")
    suspend fun downloadMateriFile(@Path("id") id: String): ResponseBody

    @GET("api/materi/{id}/progress")
    suspend fun getProgress(@Path("id") id: String): ProgressBaca

    @PUT("api/materi/{id}/progress")
    suspend fun updateProgress(
        @Path("id") id: String,
        @Body body: UpdateProgressRequest
    ): ProgressBaca

    @GET("api/materi/{materiId}/bookmarks")
    suspend fun listBookmarks(@Path("materiId") materiId: String): List<Bookmark>

    @POST("api/materi/{materiId}/bookmarks")
    suspend fun createBookmark(
        @Path("materiId") materiId: String,
        @Body body: CreateBookmarkRequest
    ): Bookmark

    @DELETE("api/bookmarks/{id}")
    suspend fun deleteBookmark(@Path("id") id: String): DeletedResponse
}
