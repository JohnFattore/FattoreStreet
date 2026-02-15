import { Spinner, Card } from "react-bootstrap";

import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
} from "recharts";

/**
 * Single-line usage:  <GenericLineChart data={[...]} label="Title" strokeColor="#007bff" />
 * Multi-line overlay: <GenericLineChart data={mergedData} label="Title" lines={[{ dataKey: "a", color: "#007bff", name: "A" }, ...]} />
 */
export default function GenericLineChart({ data, label, description, strokeColor = "#8884d8", height = 300, lines }: {
  data: object[] | null | undefined;
  label: string;
  description: string;
  strokeColor?: string;
  height?: number;
  lines?: { dataKey: string; color: string; name: string }[];
}) {
  if (!data) {
    return (
      <Card>
        <Card.Body style={{ minHeight: height + 100 }}>
          <Card.Title>{label}</Card.Title>
          <Spinner animation="border" variant="primary" />
        </Card.Body>
      </Card>
    );
  }

  return (
    <Card>
      <Card.Body>
        <Card.Title>
          {label}
        </Card.Title>
        <div style={{ width: "100%", height }}>
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
              {lines ? (
                <>
                  <Legend />
                  {lines.map((line) => (
                    <Line
                      key={line.dataKey}
                      type="monotone"
                      dataKey={line.dataKey}
                      name={line.name}
                      stroke={line.color}
                      strokeWidth={3}
                      dot={false}
                      activeDot={{ r: 6, strokeWidth: 0 }}
                    />
                  ))}
                </>
              ) : (
                <Line
                  type="monotone"
                  dataKey="value"
                  stroke={strokeColor}
                  strokeWidth={3}
                  dot={false}
                  activeDot={{ r: 6, strokeWidth: 0 }}
                />
              )}
            </LineChart>
          </ResponsiveContainer>
        </div>
        {description && (
          <div className="mt-4 pt-3 border-top">
            <p style={{ fontSize: '0.9rem', lineHeight: '1.6' }}>
              {description}
            </p>
          </div>
        )}
      </Card.Body>
    </Card>
  );
}
