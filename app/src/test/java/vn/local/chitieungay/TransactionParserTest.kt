package vn.local.chitieungay

import org.junit.Assert.*
import org.junit.Test

class TransactionParserTest {
    @Test fun debitFromSignedAmount() {
        val p = TransactionParser.parse("Thông báo biến động", "TK 123 GD -250.000 VND. Số dư 4.700.000 VND")
        assertNotNull(p)
        assertEquals(250000L, p!!.amount)
        assertEquals("expense", p.direction)
    }
    @Test fun creditWithWording() {
        val p = TransactionParser.parse("Biến động số dư", "Nhận tiền 1.500.000đ từ khách hàng. Số dư 9.000.000đ")
        assertNotNull(p)
        assertEquals(1500000L, p!!.amount)
        assertEquals("income", p.direction)
    }
    @Test fun balanceOnlyIsNotATransaction() {
        assertNull(TransactionParser.parse("Số dư", "Số dư: 4.000.000 VND"))
    }
    @Test fun promoNotTransaction() {
        assertNull(TransactionParser.parse("Ưu đãi", "Giảm ngay 250.000đ cho món ăn yêu thích"))
    }
    @Test fun fingerprintsDedupWithinTwoMinuteBucket() {
        val a=TransactionParser.fingerprint("com.bank", "a", "-200.000 VND", 120_000)
        val b=TransactionParser.fingerprint("com.bank", "a", "-200.000 VND", 150_000)
        assertEquals(a,b)
    }
}
