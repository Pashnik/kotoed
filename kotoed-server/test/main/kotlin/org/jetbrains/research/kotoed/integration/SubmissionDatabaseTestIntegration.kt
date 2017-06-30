package org.jetbrains.research.kotoed.integration

import io.vertx.core.Vertx
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import org.jetbrains.research.kotoed.eventbus.Address
import org.jetbrains.research.kotoed.util.AnyAsJson
import org.jetbrains.research.kotoed.util.Jsonable
import org.jetbrains.research.kotoed.util.Loggable
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import java.util.concurrent.Future
import kotlin.test.assertEquals

inline fun whileEx(condition: () -> Boolean, maxTries: Int = Int.MAX_VALUE, body: () -> Unit) {
    var tries = 0
    while (condition() && tries < maxTries) {
        ++tries; body(); }
}

class SubmissionDatabaseTestIntegration : Loggable {

    companion object {
        lateinit var server: Future<Vertx>
        @JvmStatic
        @BeforeClass
        fun before() {
            server = startServer()
            server.get()

            try {
                setupTC()
            } catch (ex: Exception) {
            }

        }

        @JvmStatic
        @AfterClass
        fun after() {
            stopServer(server)
        }
    }

    fun dbPost(address: String, payload: Jsonable) =
            wpost("debug/eventbus/$address", payload = payload.toJson().encode())
                    .let(::JsonObject)

    fun dbPostMany(address: String, payload: Jsonable) =
            wpost("debug/eventbus/$address", payload = payload.toJson().encode())
                    .let(::JsonArray)

    fun makeDbNew(entity: String, payload: Jsonable) =
            dbPost(Address.Api.create(entity), payload).getJsonObject("record")

    val JsonObject.id: Int? get() = getInteger("id")

    @Test
    fun testSimple() = with(AnyAsJson) {
        val user = makeDbNew(
            "denizen",
            object: Jsonable {
                val denizenId = "Vasyatka"
                val password = ""
                val salt = ""
            }
        )

        val course = makeDbNew(
            "course",
            object: Jsonable {
                val name = "Transmogrification 101"
                val buildTemplateId = "Test_build_template_id"
                val rootProjectId = "_Root"
            }
        )

        val project = makeDbNew(
            "project",
            object : Jsonable {
                val name = "Da_supa_mega_project"
                val denizen_id = user.id
                val course_id = course.id
                val repo_type = "mercurial"
                val repo_url = "http://bitbucket.org/vorpal-research/kotoed"
            }
        )


        var submission = makeDbNew(
            "submission",
            object : Jsonable {
                val projectId = project.id
                val revision = "1942a948d720fb786fc8c2e58af335eea2e2fe90"
            }
        )

        assert(submission.id is Int)

        whileEx({ submission.getString("state") != "open" }, maxTries = 200) {
            submission = dbPost(
                Address.Api.Submission.Read,
                object : Jsonable {
                    val id = submission.id
                }
            ).getJsonObject("record")
            Thread.sleep(100)
        }

        var resubmission = dbPost(
                Address.Api.Submission.Create,
                object : Jsonable {
                    val parent_submission_id = submission.id
                    val project_id = project.id
                    val revision = "82b75aa179ef4d20b2870df88c37657ecb2b9f6b"
                }
        ).getJsonObject("record")

        whileEx({ resubmission.getString("state") != "open" }, maxTries = 200) {
            resubmission = dbPost(
                    Address.Api.Submission.Read,
                    object : Jsonable {
                        val id = resubmission.id
                    }
            ).getJsonObject("record")
            Thread.sleep(100)
        }

        val comment = dbPost(
                Address.Api.Submission.Comment.Create,
                object : Jsonable {
                    val submission_id = submission.id
                    val sourcefile = "pom.xml"
                    val sourceline = 2
                    val text = "tl;dr"
                }
        ).getJsonObject("record")

        println(comment)

        assertEquals(resubmission.id, comment.getInteger("submission_id"))

        var resubmission2 = dbPost(
                Address.Api.Submission.Create,
                object : Jsonable {
                    val parent_submission_id = resubmission.id
                    val project_id = project.id
                    val revision = "9fc0841dcdfaf274fc9b71a790dd6a46d21731d8"
                }
        ).getJsonObject("record")

        whileEx({ resubmission2.getString("state") != "open" }, maxTries = 200) {
            resubmission2 = dbPost(
                    Address.Api.Submission.Read,
                    object : Jsonable {
                        val id = resubmission2.id
                    }
            ).getJsonObject("record")
            Thread.sleep(100)
        }

        val comments = dbPostMany(
                Address.Api.Submission.Comments,
                object : Jsonable {
                    val id = resubmission2.id
                }
        )

        assertEquals(1, comments.size())
        assertEquals(comment.getString("text"), comments.getJsonObject(0).getString("text"))
    }

}