package com.github.spud.tinystore.infrastrucutre.domain.order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@Entity
@Table(name = "order_status_history", schema = "order_db")
public class OrderStatusHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "id", nullable = false)
	private UUID id;

	@NotNull
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@OnDelete(action = OnDeleteAction.CASCADE)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@Size(max = 255)
	@NotNull
	@Column(name = "reason", nullable = false)
	private String reason;

	@NotNull
	@ColumnDefault("now()")
	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

/*
 TODO [Reverse Engineering] create field to map the 'from_status' column
 Available actions: Define target Java type | Uncomment as is | Remove column mapping
    @Column(name = "from_status", columnDefinition = "order_status")
    private Object fromStatus;
*/
/*
 TODO [Reverse Engineering] create field to map the 'to_status' column
 Available actions: Define target Java type | Uncomment as is | Remove column mapping
    @Column(name = "to_status", columnDefinition = "order_status not null")
    private Object toStatus;
*/
}