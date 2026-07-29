package jp.shiguredo.sora.sdk

/**
 * テストで利用する PEM 形式の証明書・秘密鍵のフィクスチャ。
 *
 * モックやスタブは利用せず、openssl (LibreSSL) で実際に生成した証明書・秘密鍵を埋め込んでいる。
 * ユニットテストにのみ利用し、サーバーには紐付けないこと。
 * 生成手順の概要:
 * - RSA 秘密鍵 (PKCS#8): `openssl genpkey -algorithm RSA`
 * - EC 秘密鍵 (PKCS#8): `openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256`
 * - 自己署名証明書: `openssl req -new -x509 -key <key> -subj "/CN=<name>"`
 * - PKCS#1 秘密鍵: `openssl rsa -in <pkcs8>`
 */
internal object TestPemFixtures {
    // CN=sora-test-ca-1 の自己署名証明書
    val certificate1 =
        """
        -----BEGIN CERTIFICATE-----
        MIICrjCCAZYCCQCxMkhs2JLUqjANBgkqhkiG9w0BAQsFADAZMRcwFQYDVQQDDA5z
        b3JhLXRlc3QtY2EtMTAeFw0yNjA3MTAwNzQ1MjJaFw0zNjA3MDcwNzQ1MjJaMBkx
        FzAVBgNVBAMMDnNvcmEtdGVzdC1jYS0xMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A
        MIIBCgKCAQEAoGJQVIiZWfWeWrwh9Exc9nVLWWN12M5HH3ADSodyWdbWQzddJTkr
        uf1J9J6Z+ccdBywmzb4dZIpUB9S+qYo8xnPnF2zyoMJpGM97fqkqJ98uYTjCuTo1
        RSF5niwX8jV1kFJga+zfjqUdA2lHOVW6bMtmakowavTNlKN/M4LB4T1XTEwiG0AL
        EtieLmSQ6nHzc1u1iUkN+sxd8mV+HBh8N272Yfv6JvhC2xxq3Q1uapHRFpkKquYl
        2bW9B16H58eT8HR/RpMaphQpPe+aaY+ReYN4BCbB2elQu/pgS7y3iUbipdstL74q
        IrvL8oCm6j+Bz7h3QtUHdHOSAW2aNcuQNQIDAQABMA0GCSqGSIb3DQEBCwUAA4IB
        AQCP4HIfZANOYhVp92p/qvLhyMhQ1Lxt6pW/9HruLsawon8m/hzyxof12/EjHWm/
        xj3q3kAizf0cHnb46CH0gF/UcVUsUe4jAtPIw9Jghmbg5EUHQzu0eSB7QBPdS2Uz
        dOiiV28GyrTSluB38pRHzKF8otPLNPY13Kbr6vflcZSzb+K7l4OoyH6B4T8UI6ax
        dD5B+8+5pcdWbn62RSa6GIJDKwySh3d3RENqVZ9ugM1Ge1iwFEtdud+uQC3UUQVM
        bc7G1o9vYI+yiQeQ1EiZ+VZgqFs2uLlPPahQ8JQnXn0fddUIjuAJiF9KEv3rq57x
        nylRR1kn7Tv/ENErTcIP/8YY
        -----END CERTIFICATE-----
        """.trimIndent()

    // CN=sora-test-ca-2 の自己署名証明書
    val certificate2 =
        """
        -----BEGIN CERTIFICATE-----
        MIICrjCCAZYCCQCqsb5xNrmVMjANBgkqhkiG9w0BAQsFADAZMRcwFQYDVQQDDA5z
        b3JhLXRlc3QtY2EtMjAeFw0yNjA3MTAwNzQ1MjJaFw0zNjA3MDcwNzQ1MjJaMBkx
        FzAVBgNVBAMMDnNvcmEtdGVzdC1jYS0yMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A
        MIIBCgKCAQEA5R/Q4B4CXg2ELGV4ldr6KCLKi7vdfzS1qpPTStlAT7BBKizRwWKI
        7+KBj80y+GcXra+1r5L47iQ4pGfkavzCGCpsIsfYTfAykU52yEyaSvprayQlFIif
        frbYBxa7fyRdEoTynVIvWiaQtFldcA7Yu6pEYEkA6hhsYhCcB5mzCZlNuvWRRcIW
        q4Zx8SSNbFPFdAXCHaQpM6jHSDiQpe6rBB3OMeqdHw8jKFzOKqA8M647qFmrsH/Z
        VEFiCss/SNcOwNWnOH7LU4kzEAR/ISKYyLeE9yFmbtvEYoJokzD+wN2gUM/VDIvh
        AeyAAa7QtYUvqihQlrZwnIvv2JRa/Nr+dwIDAQABMA0GCSqGSIb3DQEBCwUAA4IB
        AQBWY/Vcj5aQ/m2IxMLYU/ZMl18VjrBgzGKC5Z8FViJEsritElXbUyIFVYlVSaPA
        C9KtCJIXU/0vnAzxbIfutVH6JoqrZsd44wT5Ozgo50bEujuxkRbhNIMRgoy0eFoQ
        sm34Op+z/OqSzrGQJZlaL7rzTr5NRc+cZOVrhAeJA312euunzQ2sL/yJQ0iRgAPC
        hP6/GVj88gJqn/CVEVjSCm8wI03wUBM/H2q83BkABiTA619wVsQps5fZb5Zycn4P
        Mzmwt3dUrK7YIQeACP2G6nfIYfTQr0GzwBRAzfyVGmxUxX963JMLWmpnZpjFsOQF
        OP2elXkjs6jvW6QsIYHy9RzG
        -----END CERTIFICATE-----
        """.trimIndent()

    // certificate1 と certificate2 を連結した証明書チェーン
    val certificateChain = certificate1 + "\n" + certificate2

    // certificate1 に対応する RSA 秘密鍵 (PKCS#8)
    val rsaPrivateKeyPkcs8 =
        """
        -----BEGIN PRIVATE KEY-----
        MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQCgYlBUiJlZ9Z5a
        vCH0TFz2dUtZY3XYzkcfcANKh3JZ1tZDN10lOSu5/Un0npn5xx0HLCbNvh1kilQH
        1L6pijzGc+cXbPKgwmkYz3t+qSon3y5hOMK5OjVFIXmeLBfyNXWQUmBr7N+OpR0D
        aUc5Vbpsy2ZqSjBq9M2Uo38zgsHhPVdMTCIbQAsS2J4uZJDqcfNzW7WJSQ36zF3y
        ZX4cGHw3bvZh+/om+ELbHGrdDW5qkdEWmQqq5iXZtb0HXofnx5PwdH9GkxqmFCk9
        75ppj5F5g3gEJsHZ6VC7+mBLvLeJRuKl2y0vvioiu8vygKbqP4HPuHdC1Qd0c5IB
        bZo1y5A1AgMBAAECggEBAJBT6LYpttadkcNVSbjufznKk+P8/S/9cUN5KX0IJn9y
        NZ6HU+sLZ64XoVXg4+9Cn3y03raHPywaz0O8z1cCb26nHm6WPEEusiUBkcCJusXm
        sXYL/i9xkj6DwU31oBb2xLT7bImyv/s92r4XH2EZJIqC/8bmvGuDoP0BpJZWNOLb
        Q8sh155s05k45a1r1lNeGjGjOJePUt6OvedA5ctu/pWQf96xcS7B6QIUKxjcIpQw
        C07o2o/vR2o9l1LQ9AmGSD53CUEdJCYY2/s06/qrI0TtcK3mwNtWp4ha6w0AoWwQ
        8Qziur8cWuTAA/3E3gfSFiVmK2OO1GYHYHUl4TN7DAECgYEAz0GDU+fwRBuksw6y
        /dESY0t0W38YwOa3SGJ3AH9BwSH823hOEVsUWcLmHcaROtgpVM6XjGAYYqhRa3xx
        +CC55MsiP/birfs6r7+AnWOGXd4UCLy9RjOb5a60QiFLeMBAYqoLTuNcQLOBC7aU
        iZVi0ismlOrvACQKhYp9CztCcF0CgYEAxhq74B4tznCwhSQQA1ZDchWUYbPBjKLe
        XxX6qFdve06b0npXlMI5GCpruNIc7uHrVc819TmtBMii9RmucRqPNFWGKfRDOR61
        WbyW0pcXGdcXaijj7qcsNSlSZWOevj51W5mo8S5q93WAlOfY0z2YWqurgi35gpH9
        yWOawfw4AbkCgYBcyrofsPJzq+S7flNJLHgNGNVJuce9Zg6dS+h3woIQFEV/hYd+
        YcbkwUwB/Ms9C1bF75EOel+wnCeH9jmYnB5ef0wgU0r+FkMaOKU+0jZwhGN33fjo
        G2crGGMAUKPXtkudYQCbG1RMa5HVSrOKPeX2rvchKWZEK97CF1UQ2EFQyQKBgH2g
        hnPhr3qyy74i2GTFV5AJT0eGDr94qTvzXDlU+UVg3D/lhZS4dix0+ksCM4bpjauk
        87rHEIlwEqcL2iuvhBDUC3ifheG7L5XwmlSBrAye8iJIPAMj0E0GH1JcklZilVm5
        YAFSRlMXGKtVO5L6BJu7MdAkB45dtmr31zQdFgdBAoGBAIPj+WRSVthM9odzh5hU
        Ethr7GTjWkG+iR9kEprU3aQLNPHjv1C570G7+U5hnTrIwQh8Wx+ES6bcJJJvcfwI
        OwhxZYhSyVELQgfSRza6cCPwwGXkm7+PpuyKfwkMPcp/J/O/ptHJlXLJl4dq1z80
        sS6dL2/9r1qenQvX2hL5XWdA
        -----END PRIVATE KEY-----
        """.trimIndent()

    // EC 秘密鍵 (PKCS#8, P-256)
    val ecPrivateKeyPkcs8 =
        """
        -----BEGIN PRIVATE KEY-----
        MIIBeQIBADCCAQMGByqGSM49AgEwgfcCAQEwLAYHKoZIzj0BAQIhAP////8AAAAB
        AAAAAAAAAAAAAAAA////////////////MFsEIP////8AAAABAAAAAAAAAAAAAAAA
        ///////////////8BCBaxjXYqjqT57PrvVV2mIa8ZR0GsMxTsPY7zjw+J9JgSwMV
        AMSdNgiG5wSTamZ44ROdJreBn36QBEEEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg
        9KE5RdiYwpZP40Li/hp/m47n60p8D54WK84zV2sxXs7LtkBoN79R9QIhAP////8A
        AAAA//////////+85vqtpxeehPO5ysL8YyVRAgEBBG0wawIBAQQg2GJHJHtedsfl
        iALRXeVgcL3/t1zMufxMAJwYp4wdtrShRANCAAQVLsm1QK/HyT8RhT3Ji1PQ/jzb
        tnIEc2slrZ8SSUm08spn1u5yxqEKz8IVz1PKPTeKWajIf78LOAR1aPH+SDin
        -----END PRIVATE KEY-----
        """.trimIndent()

    // PKCS#1 形式の RSA 秘密鍵 (非対応フォーマット確認用)
    val rsaPrivateKeyPkcs1 =
        """
        -----BEGIN RSA PRIVATE KEY-----
        MIIEpAIBAAKCAQEAoGJQVIiZWfWeWrwh9Exc9nVLWWN12M5HH3ADSodyWdbWQzdd
        JTkruf1J9J6Z+ccdBywmzb4dZIpUB9S+qYo8xnPnF2zyoMJpGM97fqkqJ98uYTjC
        uTo1RSF5niwX8jV1kFJga+zfjqUdA2lHOVW6bMtmakowavTNlKN/M4LB4T1XTEwi
        G0ALEtieLmSQ6nHzc1u1iUkN+sxd8mV+HBh8N272Yfv6JvhC2xxq3Q1uapHRFpkK
        quYl2bW9B16H58eT8HR/RpMaphQpPe+aaY+ReYN4BCbB2elQu/pgS7y3iUbipdst
        L74qIrvL8oCm6j+Bz7h3QtUHdHOSAW2aNcuQNQIDAQABAoIBAQCQU+i2KbbWnZHD
        VUm47n85ypPj/P0v/XFDeSl9CCZ/cjWeh1PrC2euF6FV4OPvQp98tN62hz8sGs9D
        vM9XAm9upx5uljxBLrIlAZHAibrF5rF2C/4vcZI+g8FN9aAW9sS0+2yJsr/7Pdq+
        Fx9hGSSKgv/G5rxrg6D9AaSWVjTi20PLIdeebNOZOOWta9ZTXhoxoziXj1Lejr3n
        QOXLbv6VkH/esXEuwekCFCsY3CKUMAtO6NqP70dqPZdS0PQJhkg+dwlBHSQmGNv7
        NOv6qyNE7XCt5sDbVqeIWusNAKFsEPEM4rq/HFrkwAP9xN4H0hYlZitjjtRmB2B1
        JeEzewwBAoGBAM9Bg1Pn8EQbpLMOsv3REmNLdFt/GMDmt0hidwB/QcEh/Nt4ThFb
        FFnC5h3GkTrYKVTOl4xgGGKoUWt8cfggueTLIj/24q37Oq+/gJ1jhl3eFAi8vUYz
        m+WutEIhS3jAQGKqC07jXECzgQu2lImVYtIrJpTq7wAkCoWKfQs7QnBdAoGBAMYa
        u+AeLc5wsIUkEANWQ3IVlGGzwYyi3l8V+qhXb3tOm9J6V5TCORgqa7jSHO7h61XP
        NfU5rQTIovUZrnEajzRVhin0QzketVm8ltKXFxnXF2oo4+6nLDUpUmVjnr4+dVuZ
        qPEuavd1gJTn2NM9mFqrq4It+YKR/cljmsH8OAG5AoGAXMq6H7Dyc6vku35TSSx4
        DRjVSbnHvWYOnUvod8KCEBRFf4WHfmHG5MFMAfzLPQtWxe+RDnpfsJwnh/Y5mJwe
        Xn9MIFNK/hZDGjilPtI2cIRjd9346BtnKxhjAFCj17ZLnWEAmxtUTGuR1Uqzij3l
        9q73ISlmRCvewhdVENhBUMkCgYB9oIZz4a96ssu+IthkxVeQCU9Hhg6/eKk781w5
        VPlFYNw/5YWUuHYsdPpLAjOG6Y2rpPO6xxCJcBKnC9orr4QQ1At4n4Xhuy+V8JpU
        gawMnvIiSDwDI9BNBh9SXJJWYpVZuWABUkZTFxirVTuS+gSbuzHQJAeOXbZq99c0
        HRYHQQKBgQCD4/lkUlbYTPaHc4eYVBLYa+xk41pBvokfZBKa1N2kCzTx479Que9B
        u/lOYZ06yMEIfFsfhEum3CSSb3H8CDsIcWWIUslRC0IH0kc2unAj8MBl5Ju/j6bs
        in8JDD3Kfyfzv6bRyZVyyZeHatc/NLEunS9v/a9anp0L19oS+V1nQA==
        -----END RSA PRIVATE KEY-----
        """.trimIndent()
}
