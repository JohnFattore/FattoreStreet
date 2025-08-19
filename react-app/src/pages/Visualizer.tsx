import BenchmarkCompareTable from '../components/BenchmarkCompareTable';
import { useSelector } from "react-redux";
import { RootState } from "../main";
import LoginForm from '../components/LoginForm';
export default function Visualizer() {
    const { access } = useSelector((state: RootState) => state.user);
    if (!access) {
        return <LoginForm />
    }
    return (
        <>
            <BenchmarkCompareTable/>
        </>
    );
}