import { Spinner, Card } from "react-bootstrap";

import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  ResponsiveContainer,
} from "recharts";

export default function GenericLineChart({ data, label }) {
  if (!data) {
    return (
      <>
        <h3>{label}</h3>
        <Spinner />
      </>
    );
  }
  return (
    <>
      <h3>{label}</h3>
      <Card>
        <div style={{ width: "100%", height: 400 }}>
          <ResponsiveContainer>
            <LineChart data={data}>
              <XAxis dataKey="date" />
              <YAxis domain={["auto", "auto"]} />
              <Tooltip />
              <CartesianGrid stroke="#eee" strokeDasharray="5 5" />
              <Line type="monotone" dataKey="value" stroke="#8884d8" />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </Card>
    </>
  );
}
