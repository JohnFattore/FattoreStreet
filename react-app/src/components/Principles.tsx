import principles from "../data/principles.json";
import { Accordion, Table } from "react-bootstrap";

export default function Principles() {
  return (
    <>
      <h1>Fattore's Investing Principles</h1>
      <p>{principles.intro}</p>
      <Accordion defaultActiveKey="0">
        {principles.principles.map((principle) => (
          <Accordion.Item eventKey={String(principle.id)} key={principle.id}>
            <Accordion.Header>{principle.title}</Accordion.Header>
            <Accordion.Body>
              <p>{principle.content}</p>
              {principle.table && (
                <Table>
                  <thead>
                    <tr>
                      {Object.keys(principle.table.data[0]).map((h) => (
                        <th key={h}>{h}</th>
                      ))}
                    </tr>
                  </thead>
                  <tbody>
                    {principle.table.data.map((row, i) => (
                      <tr key={i}>
                        {Object.keys(principle.table.data[0]).map((header, j) => (
                          <td key={j}>{row[header]}</td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </Table>
              )}
            </Accordion.Body>
          </Accordion.Item>
        ))}
      </Accordion>
    </>
  );
}
