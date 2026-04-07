import grpc
from app.proto import preprocessing_pb2 as preprocessing__pb2
from app.proto import preprocessing_pb2_grpc as preprocessing_grpc

from app.core.logger import get_logger

logger = get_logger(__name__)


def call_cpp_preprocessing(request_data: dict, timeout=40):
    try:
        channel = grpc.insecure_channel("evalai-cpp:50051")
        stub = preprocessing_grpc.PreprocessingServiceStub(channel)

        pages = []
        for idx, path in enumerate(request_data["raw_image_paths"]):
            pages.append(
                preprocessing__pb2.RawImagePage(
                    page_number=idx + 1,
                    image_path=path
                )
            )

        grpc_request = preprocessing__pb2.PreprocessRequest(
            task_id=request_data["task_id"],
            exam_id=request_data["context"]["exam_id"],
            student_id=request_data["student"]["student_id"],
            answer_sheet_id=request_data["student"]["answer_sheet_id"],
            output_base_path="/app/processed",
            pages=pages
        )

        logger.info("[CPP] Calling preprocessing service...")

        response = stub.PreprocessStudentImages(grpc_request, timeout=timeout)

        cleaned_paths = [
            page.cleaned_path for page in response.pages
        ]

        logger.info(f"[CPP] Preprocessing done | pages: {len(cleaned_paths)}")

        return cleaned_paths

    except Exception as e:
        logger.error(f"[CPP] Failed: {e}")
        return None