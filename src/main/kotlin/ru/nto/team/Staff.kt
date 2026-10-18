package ru.nto.team

import jakarta.persistence.*

@Entity
@Table(name = "staff")
class Staff(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(nullable = false, unique = true, length = 4)
    var code: String = "",
    @Column(name = "full_name", nullable = false, length = 150)
    var fullName: String = "",
)
