package app.echo.android.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ITunesPlaylistCodecTest {
    @Test
    fun readsPlaylistOrderAndSkipsMasterLibrary() {
        val parsed = ITunesPlaylistCodec.parse(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple Computer//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0"><dict>
              <key>Tracks</key><dict>
                <key>10</key><dict><key>Name</key><string>First</string><key>Location</key><string>file://localhost/Music/First.flac</string></dict>
                <key>20</key><dict><key>Name</key><string>Second &amp; last</string><key>Location</key><string>file://localhost/Music/Second%20song.mp3</string></dict>
              </dict>
              <key>Playlists</key><array>
                <dict><key>Name</key><string>Library</string><key>Master</key><true/>
                  <key>Playlist Items</key><array><dict><key>Track ID</key><integer>10</integer></dict></array>
                </dict>
                <dict><key>Name</key><string>Evening</string><key>Playlist Persistent ID</key><string>ABC123</string>
                  <key>Playlist Items</key><array>
                    <dict><key>Track ID</key><integer>20</integer></dict>
                    <dict><key>Track ID</key><integer>10</integer></dict>
                  </array>
                </dict>
              </array>
            </dict></plist>
            """.trimIndent(),
        ).single()
        assertEquals("ABC123", parsed.key)
        assertEquals("Evening", parsed.name)
        assertEquals(listOf("Second & last", "First"), parsed.entries.map { it.title })
        assertEquals("file://localhost/Music/Second%20song.mp3", parsed.entries.first().location)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCustomEntities() {
        ITunesPlaylistCodec.parse("""<!DOCTYPE plist [<!ENTITY private SYSTEM "file:///private">]><plist>&private;</plist>""")
    }
}
