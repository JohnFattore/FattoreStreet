import { Card, Table } from "react-bootstrap";
import { ISECData } from "../interfaces";
import { formatString } from "../functions/helperFunctions";

export default function LatestBalanceSheet({ data }: { data: ISECData }) {
    return (
        <Card>
            <Card.Header as="h5" className="bg-info text-white">Latest Balance Sheet</Card.Header>
            <Card.Body>
                <Table hover size="sm">
                    <tbody>
                        <tr>
                            <td><strong>Total Assets</strong></td>
                            <td>{formatString(Number(data.latestAssets), "money")}</td>
                        </tr>
                        <tr>
                            <td><strong>Total Liabilities</strong></td>
                            <td>{formatString(Number(data.latestLiabilities), "money")}</td>
                        </tr>
                        <tr>
                            <td><strong>Total Equity</strong></td>
                            <td>{formatString(Number(data.latestEquity), "money")}</td>
                        </tr>
                        <tr>
                            <td><strong>Cash & Equivalents</strong></td>
                            <td>{formatString(Number(data.latestCash), "money")}</td>
                        </tr>
                        <tr>
                            <td><strong>Inventory</strong></td>
                            <td>{formatString(Number(data.latestInventory), "money")}</td>
                        </tr>
                        <tr>
                            <td><strong>Latest EPS</strong></td>
                            <td>${data.latestEps}</td>
                        </tr>
                    </tbody>
                </Table>
            </Card.Body>
        </Card>
    );
}
