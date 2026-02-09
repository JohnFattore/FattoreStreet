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

export default function GenericLineChart({ data, label, description, strokeColor = "#8884d8", height = 300 }) {
  if (!data) {
    return (
      <Card className="h-100 shadow-sm border-0">
        <Card.Body className="d-flex flex-column align-items-center justify-content-center" style={{ minHeight: height + 100 }}>
          <Card.Title className="text-muted mb-4">{label}</Card.Title>
          <Spinner animation="border" variant="primary" />
        </Card.Body>
      </Card>
    );
  }

  return (
    <Card className="h-100 shadow-sm border-0 overflow-hidden transition-hover">
      <Card.Body className="p-4 d-flex flex-column">
        <Card.Title className="fw-bold mb-4 text-dark">
          {label}
        </Card.Title>
        <div className="flex-grow-1" style={{ width: "100%", height }}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data} margin={{ top: 5, right: 30, left: 0, bottom: 5 }}>
              <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f0f0f0" />
              <XAxis
                dataKey="date"
                axisLine={false}
                tickLine={false}
                tick={{ fill: '#999', fontSize: 12 }}
                minTickGap={30}
              />
              <YAxis
                domain={["auto", "auto"]}
                axisLine={false}
                tickLine={false}
                tick={{ fill: '#999', fontSize: 12 }}
              />
              <Tooltip
                contentStyle={{
                  borderRadius: '10px',
                  border: 'none',
                  boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
                }}
              />
              <Line
                type="monotone"
                dataKey="value"
                stroke={strokeColor}
                strokeWidth={3}
                dot={false}
                activeDot={{ r: 6, strokeWidth: 0 }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
        {description && (
          <div className="mt-4 pt-3 border-top">
            <p className="text-muted mb-0" style={{ fontSize: '0.9rem', lineHeight: '1.6' }}>
              {description}
            </p>
          </div>
        )}
      </Card.Body>
    </Card>
  );
}
