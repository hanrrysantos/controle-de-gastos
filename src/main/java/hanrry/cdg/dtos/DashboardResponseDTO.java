package hanrry.cdg.dtos;

public class DashboardResponseDTO {
    private Double totalReceitas;
    private Double totalDespesas;
    private Double saldo;

    public DashboardResponseDTO(Double totalReceitas, Double totalDespesas, Double saldo) {
        this.totalReceitas = totalReceitas;
        this.totalDespesas = totalDespesas;
        this.saldo = saldo;
    }

    public Double getTotalReceitas() { return totalReceitas; }
    public void setTotalReceitas(Double totalReceitas) { this.totalReceitas = totalReceitas; }

    public Double getTotalDespesas() { return totalDespesas; }
    public void setTotalDespesas(Double totalDespesas) { this.totalDespesas = totalDespesas; }

    public Double getSaldo() { return saldo; }
    public void setSaldo(Double saldo) { this.saldo = saldo; }
}