import { Modal, Spinner } from "react-bootstrap";

interface LoadingModalProps {
    show: boolean;
    message?: string;
}

export default function LoadingModal({ show, message = "Loading Portfolio Data..." }: LoadingModalProps) {
    return (
        <Modal show={show} centered backdrop="static" keyboard={false}>
            <Modal.Body className="text-center p-5">
                <Spinner animation="border" className="mb-3" style={{ width: '3rem', height: '3rem' }} />
                <h4>{message}</h4>
                <p className="mb-0">Please wait while we fetch the latest financial data.</p>
            </Modal.Body>
        </Modal>
    );
}
