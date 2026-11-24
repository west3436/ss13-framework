package io.github.west3436.ss13.gradle

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.Property

/**
 * The {@code ss13} block, which each of the plugins adds to a project:
 *
 * <pre>
 * ss13 {
 *     githubRepo = 'west3436/expedition'                 // POM links, the API baseline's releases
 *     apiPackage = 'io.github.west3436.expedition.api'   // io.github.west3436.ss13.api only
 * }
 * </pre>
 */
abstract class Ss13Extension {
    /** The project's GitHub repository, as owner/name: the POM's links, and where its releases are. */
    abstract Property<String> getGithubRepo()

    /**
     * The API's package. The API jar holds nothing outside it, and it is the jar's
     * Automatic-Module-Name.
     */
    abstract Property<String> getApiPackage()

    /** The project's {@code ss13} block, added if it has none yet. */
    static Ss13Extension of(Project project) {
        def found = project.extensions.findByType(Ss13Extension)
        found ?: project.extensions.create('ss13', Ss13Extension)
    }

    /**
     * The versions a nested copy of a jar at this version accepts: from this one up to the next
     * version that may break it. That is the next minor while it is 0.x, and the next major from 1.0.
     */
    String versionRange(String version) {
        versionRange(version, false)
    }

    /**
     * As {@link #versionRange(String)}. A snapshot's floor is the snapshot itself, since
     * 0.1.0-SNAPSHOT sorts below 0.1.0 and FML would otherwise refuse the copy that is nested.
     */
    String versionRange(String version, boolean snapshot) {
        def m = version =~ /^(\d+)\.(\d+)\.\d+/
        if (!m.find()) {
            throw new GradleException("ss13.versionRange: not a semver version: ${version}")
        }
        int major = m.group(1) as int
        int minor = m.group(2) as int
        def floor = snapshot ? "${version}-SNAPSHOT" : version
        major == 0 ? "[${floor},0.${minor + 1}.0)" : "[${floor},${major + 1}.0)"
    }
}
