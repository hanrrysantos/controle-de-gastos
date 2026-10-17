package hanrry.cdg.dtos;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;

public class DashboardResponseDTO {
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalReceitas;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalDespesas;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal saldo;

    public DashboardResponseDTO(BigDecimal totalReceitas, BigDecimal totalDespesas, BigDecimal saldo) {
        this.totalReceitas = totalReceitas;
        this.totalDespesas = totalDespesas;
        this.saldo = saldo;
    }

    public BigDecimal getTotalReceitas() { return totalReceitas; }
    public void setTotalReceitas(BigDecimal totalReceitas) { this.totalReceitas = totalReceitas; }

    public BigDecimal getTotalDespesas() { return totalDespesas; }
    public void setTotalDespesas(BigDecimal totalDespesas) { this.totalDespesas = totalDespesas; }

    public BigDecimal getSaldo() { return saldo; }
    public void setSaldo(BigDecimal saldo) { this.saldo = saldo; }
}
