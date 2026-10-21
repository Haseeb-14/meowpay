package com.meowpay.transfer

import com.meowpay.TestCats
import com.meowpay.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
@ActiveProfiles("test")
class TransferApiTest @Autowired constructor(
    private val mvc: MockMvc,
    private val cats: TestCats,
) {

    private fun postTransfer(from: Long?, body: String, key: String? = UUID.randomUUID().toString()) =
        mvc.post("/api/transfers") {
            from?.let { header("X-Cat-Id", it) }
            key?.let { header("Idempotency-Key", it) }
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    @Test
    fun `requires the acting cat header`() {
        postTransfer(null, """{"toCatId":1,"amount":1}""").andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("unauthenticated") }
        }
    }

    @Test
    fun `requires an idempotency key`() {
        val a = cats.newCat(10)
        postTransfer(a.id, """{"toCatId":1,"amount":1}""", key = null).andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("missing_header") }
        }
    }

    @Test
    fun `validates the body`() {
        val a = cats.newCat(10)
        val b = cats.newCat(0)
        postTransfer(a.id, """{"toCatId":${b.id},"amount":0}""").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("validation_error") }
            jsonPath("$.detail") { value("amount: must be greater than or equal to 1") }
        }
    }

    @Test
    fun `creates a transfer, replays it on the same key, and shows it in both histories`() {
        val a = cats.newCat(50)
        val b = cats.newCat(0)
        val key = UUID.randomUUID().toString()
        val body = """{"toCatId":${b.id},"amount":20,"note":"  tuna fund  "}"""

        postTransfer(a.id, body, key).andExpect {
            status { isCreated() }
            jsonPath("$.from.id") { value(a.id!!) }
            jsonPath("$.to.name") { value(b.name) }
            jsonPath("$.amount") { value(20) }
            jsonPath("$.note") { value("tuna fund") }
        }
        postTransfer(a.id, body, key).andExpect {
            status { isOk() }
            header { string("Idempotency-Replayed", "true") }
            jsonPath("$.amount") { value(20) }
        }

        mvc.get("/api/me") { header("X-Cat-Id", a.id!!) }.andExpect {
            status { isOk() }
            jsonPath("$.balance") { value(30) }
        }
        mvc.get("/api/transfers") { header("X-Cat-Id", b.id!!) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(1) }
            jsonPath("$[0].from.name") { value(a.name) }
        }
        mvc.get("/api/ledger") { header("X-Cat-Id", a.id!!) }.andExpect {
            status { isOk() }
            jsonPath("$.length()") { value(2) }
            jsonPath("$[0].kind") { value("TRANSFER_OUT") }
            jsonPath("$[0].amount") { value(-20) }
            jsonPath("$[0].balanceAfter") { value(30) }
            jsonPath("$[0].counterparty.name") { value(b.name) }
            jsonPath("$[1].kind") { value("TOPUP") }
            jsonPath("$[1].balanceAfter") { value(50) }
        }
    }

    @Test
    fun `reports insufficient funds as 422 with a code`() {
        val a = cats.newCat(5)
        val b = cats.newCat(0)
        postTransfer(a.id, """{"toCatId":${b.id},"amount":6}""").andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.code") { value("insufficient_funds") }
        }
    }

    @Test
    fun `unknown paths and bad parameter types use the same error shape`() {
        mvc.get("/api/nope").andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("not_found") }
        }
        mvc.get("/api/me") { header("X-Cat-Id", "not-a-number") }.andExpect {
            status { isUnauthorized() }
            jsonPath("$.code") { value("unauthenticated") }
        }
        mvc.post("/api/transfers") {
            header("X-Cat-Id", 1)
            header("Idempotency-Key", "k")
            contentType = MediaType.APPLICATION_JSON
            content = "{not json"
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("malformed_body") }
        }
    }

    @Test
    fun `lists cats without authentication`() {
        mvc.get("/api/cats").andExpect {
            status { isOk() }
            jsonPath("$[?(@.name == 'Mochi')]") { exists() }
        }
    }
}
