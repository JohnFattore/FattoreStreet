import { Card, Table } from "react-bootstrap";
import { ISECData } from "../interfaces";
import { formatString } from "../functions/helperFunctions";

export default function TTMIncomeStatement({ data }: { data: ISECData }) {
    return (
        <Card>
            <Card.Header as="h5" className="bg-success text-white">TTM Income Statement</Card.Header>
            <Card.Body>
                <Table hover size="sm">
                    <tbody>
                        <tr>
                            <td><strong>TTM Revenue</strong></td>
                            <td>{formatString(Number(data.ttmRevenue), "money")}</td>
                        </tr>
                        <tr>
                            <td><strong>TTM Revenue YoY Growth</strong></td>
                            <td className={parseFloat(data.ttmRevenueYoY) >= 0 ? "text-success" : "text-danger"}>
                                {data.ttmRevenueYoY}
                            </td>
                        </tr>
                        <tr>
                            <td><strong>TTM Gross Profit</strong></td>
                            <td>{formatString(Number(data.ttmGrossProfit), "money")}</td>
                        </tr>
                        <tr>
                            <td><strong>TTM Operating Income</strong></td>
                            <td>{formatString(Number(data.ttmOperatingIncome), "money")}</td>
                        </tr>
                        <tr>
                            <td><strong>TTM Net Income</strong></td>
                            <td>{formatString(Number(data.ttmNetIncome), "money")}</td>
                        </tr>
                        <tr>
                            <td><strong>TTM Net Income YoY Growth</strong></td>
                            <td className={parseFloat(data.ttmNetIncomeYoY) >= 0 ? "text-success" : "text-danger"}>
                                {data.ttmNetIncomeYoY}
                            </td>
                        </tr>
                        <tr>
                            <td><strong>TTM Operating Cash Flow</strong></td>
                            <td>{formatString(Number(data.ttmOperatingCashFlow), "money")}</td>
                        </tr>
                    </tbody>
                </Table>
            </Card.Body>
        </Card>
    );
}
