package code.name.monkey.retromusic

import androidx.room.Room
import code.name.monkey.retromusic.auto.AutoMusicProvider
import code.name.monkey.retromusic.cast.RetroWebServer
import code.name.monkey.retromusic.db.MIGRATION_23_24
import code.name.monkey.retromusic.db.MIGRATION_24_25
import code.name.monkey.retromusic.db.RetroDatabase
import code.name.monkey.retromusic.fragments.LibraryViewModel
import code.name.monkey.retromusic.fragments.albums.AlbumDetailsViewModel
import code.name.monkey.retromusic.fragments.artists.ArtistDetailsViewModel
import code.name.monkey.retromusic.fragments.genres.GenreDetailsViewModel
import code.name.monkey.retromusic.fragments.playlists.PlaylistDetailsViewModel
import code.name.monkey.retromusic.model.Genre
import code.name.monkey.retromusic.network.provideDefaultCache
import code.name.monkey.retromusic.network.provideLastFmRest
import code.name.monkey.retromusic.network.provideLastFmRetrofit
import code.name.monkey.retromusic.network.provideOkHttp
import code.name.monkey.retromusic.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module

val networkModule = module {

    factory {
        provideDefaultCache()
    }
    factory {
        provideOkHttp(get(), get())
    }
    single {
        provideLastFmRetrofit(get())
    }
    single {
        provideLastFmRest(get())
    }
}

private val roomModule = module {

    single {
        Room.databaseBuilder(androidContext(), RetroDatabase::class.java, "playlist.db")
            .addMigrations(MIGRATION_23_24, MIGRATION_24_25)
            .fallbackToDestructiveMigration()
            .build()
    }

    factory {
        get<RetroDatabase>().playlistDao()
    }

    factory {
        get<RetroDatabase>().playCountDao()
    }

    factory {
        get<RetroDatabase>().historyDao()
    }

    factory {
        get<RetroDatabase>().shuffleHistoryDao()
    }

    single {
        ShuffleHistoryRepository(get(), CoroutineScope(Dispatchers.IO + SupervisorJob()))
    }

    single {
        RealRoomRepository(get(), get(), get())
    } bind RoomRepository::class
}

private val autoModule = module {
    single {
        AutoMusicProvider(
            androidContext(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
}

val repositoryModule = module {
    single { RealSongRepository(androidContext()) } bind SongRepository::class
    single { RealAlbumRepository(get()) } bind AlbumRepository::class
    single { RealArtistRepository(get(), get()) } bind ArtistRepository::class
    single { RealGenreRepository(androidContext().contentResolver, get()) } bind GenreRepository::class
    single { RealLastAddedRepository(get(), get(), get()) } bind LastAddedRepository::class
    single { RealPlaylistRepository(androidContext().contentResolver) } bind PlaylistRepository::class
    single { RealSearchRepository(get(), get(), get(), get(), get()) }
    single { RealTopPlayedRepository(androidContext(), get(), get(), get()) } bind TopPlayedRepository::class
    single { RealLocalDataRepository(androidContext()) } bind LocalDataRepository::class

    single {
        RealRepository(
            context = androidContext(),
            lastFMService = get(),
            songRepository = get(),
            albumRepository = get(),
            artistRepository = get(),
            genreRepository = get(),
            lastAddedRepository = get(),
            playlistRepository = get(),
            searchRepository = get(),
            topPlayedRepository = get(),
            roomRepository = get(),
            localDataRepository = get()
        )
    } bind Repository::class
}

val viewModelModule = module {

    viewModel {
        LibraryViewModel(get())
    }

    viewModel { (albumId: Long) ->
        AlbumDetailsViewModel(
            get(),
            albumId
        )
    }

    viewModel { (artistId: Long, artistName: String?) ->
        ArtistDetailsViewModel(
            get(),
            artistId,
            artistName
        )
    }

    viewModel { (genre: Genre) ->
        GenreDetailsViewModel(
            get(),
            genre
        )
    }

    viewModel { (playlistId: Long) ->
        PlaylistDetailsViewModel(
            get(),
            playlistId
        )
    }
}

val appModule = module {
    single {
        RetroWebServer(androidContext())
    }
}

val appModules = listOf(
    networkModule,
    roomModule,
    autoModule,
    repositoryModule,
    viewModelModule,
    appModule
)
