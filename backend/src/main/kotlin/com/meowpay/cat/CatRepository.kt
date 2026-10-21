package com.meowpay.cat

import org.springframework.data.jpa.repository.JpaRepository

interface CatRepository : JpaRepository<Cat, Long>
